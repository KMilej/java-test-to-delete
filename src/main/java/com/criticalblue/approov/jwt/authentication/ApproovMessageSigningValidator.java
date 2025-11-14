package com.criticalblue.approov.jwt.authentication;

import com.criticalblue.approov.jwt.sfv.SfvFormatter;
import com.criticalblue.approov.jwt.sfv.SfvParseException;
import com.criticalblue.approov.jwt.sfv.SfvParser;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.BareItem;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.BareItemType;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DictionaryEntry;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.InnerList;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.Item;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.SfvValue;
import io.jsonwebtoken.Claims;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Validates Approov HTTP message signatures referenced by the "install" label.
 */
public final class ApproovMessageSigningValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApproovMessageSigningValidator.class);
    private static final String SIGNATURE_HEADER = "Signature";
    private static final String SIGNATURE_INPUT_HEADER = "Signature-Input";
    private static final String SIGNATURE_LABEL = "install";
    private static final String CONTENT_DIGEST_HEADER = "Content-Digest";
    private static final Set<String> SUPPORTED_CONTENT_DIGEST_ALGORITHMS =
            Set.of("sha-256", "sha-512");

    private final MessageSignatureOptions options;

    public ApproovMessageSigningValidator() {
        this.options = MessageSignatureOptions.fromEnvironment();
    }

    /**
     * Ensures the request body can be read multiple times by buffering it.
     */
    public static HttpServletRequest ensureCachedRequest(HttpServletRequest request) {
        if (request == null || request instanceof CachedBodyHttpServletRequest) {
            return request;
        }
        try {
            return new CachedBodyHttpServletRequest(request);
        } catch (IOException ex) {
            LOGGER.warn("Approov message signing: failed to cache request body - {}", ex.getMessage());
            return request;
        }
    }

    /**
     * Performs message signing validation when the Approov token includes an installation public key.
     */
    public void verifyIfPresent(HttpServletRequest request, Claims claims) {
        if (request == null || claims == null) {
            return;
        }

        String installationPublicKey = claims.get("ipk", String.class);
        if (!hasText(installationPublicKey)) {
            return;
        }

        MessageSignatureResult result = verify(request, installationPublicKey);
        if (!result.success) {
            throw new ApproovAuthenticationException(result.error, HttpStatus.UNAUTHORIZED.value());
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Approov message signing: canonical payload built:\n{}", result.canonicalMessage);
        }
    }

    private MessageSignatureResult verify(HttpServletRequest request, String publicKeyBase64) {
        String signatureHeader = combineHeaderValues(Collections.list(request.getHeaders(SIGNATURE_HEADER)));
        String signatureInputHeader =
                combineHeaderValues(Collections.list(request.getHeaders(SIGNATURE_INPUT_HEADER)));

        LOGGER.debug(
                "Approov message signing: raw Signature header {}",
                signatureHeader);
        LOGGER.debug(
                "Approov message signing: raw Signature-Input header {}",
                signatureInputHeader);

        if (!hasText(signatureHeader) || !hasText(signatureInputHeader)) {
            return MessageSignatureResult.failure("Missing Signature or Signature-Input headers");
        }

        LinkedHashMap<String, DictionaryEntry> signatureDictionary;
        LinkedHashMap<String, DictionaryEntry> signatureInputDictionary;
        try {
            signatureDictionary = SfvParser.parseDictionary(signatureHeader);
        } catch (SfvParseException ex) {
            return MessageSignatureResult.failure("Failed to parse signature header: " + ex.getMessage());
        }
        try {
            signatureInputDictionary = SfvParser.parseDictionary(signatureInputHeader);
        } catch (SfvParseException ex) {
            return MessageSignatureResult.failure("Failed to parse signature-input header: " + ex.getMessage());
        }

        try {
            ensureMatchingLabels(signatureDictionary, signatureInputDictionary);
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        DictionaryEntry signatureEntry = signatureDictionary.get(SIGNATURE_LABEL);
        DictionaryEntry signatureInputEntry = signatureInputDictionary.get(SIGNATURE_LABEL);
        if (signatureEntry == null) {
            return MessageSignatureResult.failure("Signature header missing 'install' entry");
        }
        if (signatureInputEntry == null) {
            return MessageSignatureResult.failure("Signature-Input header missing 'install' entry");
        }

        byte[] signatureBytes;
        try {
            signatureBytes = extractSignatureBytes(signatureEntry);
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        InnerList signatureInputList;
        try {
            signatureInputList = extractSignatureInput(signatureInputEntry);
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        SignatureMetadata metadata;
        try {
            metadata = extractSignatureMetadata(signatureInputList.getParameters());
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        LOGGER.debug(
                "Approov message signing: metadata alg={} created={} expires={} keyId={} nonce={} tag={}",
                metadata.algorithm,
                metadata.created,
                metadata.expires,
                metadata.keyId,
                metadata.nonce,
                metadata.tag);

        if (!"ecdsa-p256-sha256".equalsIgnoreCase(metadata.algorithm)) {
            return MessageSignatureResult.failure(
                    String.format("Unsupported signature algorithm '%s'", metadata.algorithm));
        }

        MessageSignatureResult timestampResult = validateTimestamps(metadata);
        if (!timestampResult.success) {
            return timestampResult;
        }

        String canonicalPayload;
        try {
            canonicalPayload = buildCanonicalPayload(request, signatureInputList);
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        try {
            verifyContentDigest(request);
        } catch (MessageSignatureException ex) {
            return MessageSignatureResult.failure(ex.getMessage());
        }

        if (!verifySignature(publicKeyBase64, signatureBytes, canonicalPayload.getBytes(StandardCharsets.UTF_8))) {
            return MessageSignatureResult.failure("Signature verification failed");
        }

        return MessageSignatureResult.success(canonicalPayload);
    }

    private MessageSignatureResult validateTimestamps(SignatureMetadata metadata) {
        Instant now = Instant.now();
        Duration allowedSkew = options.allowedClockSkew;

        if (options.requireCreated && metadata.created == null) {
            return MessageSignatureResult.failure("Signature missing 'created' parameter");
        }
        if (options.requireExpires && metadata.expires == null) {
            return MessageSignatureResult.failure("Signature missing 'expires' parameter");
        }
        if (metadata.created != null) {
            Instant createdInstant = Instant.ofEpochSecond(metadata.created);
            if (options.maximumSignatureAge != null
                    && createdInstant.isBefore(now.minus(options.maximumSignatureAge).minus(allowedSkew))) {
                return MessageSignatureResult.failure("Signature 'created' timestamp is older than allowed");
            }
            if (createdInstant.isAfter(now.plus(allowedSkew))) {
                return MessageSignatureResult.failure("Signature 'created' timestamp is in the future");
            }
        }
        if (metadata.expires != null) {
            Instant expiresInstant = Instant.ofEpochSecond(metadata.expires);
            if (expiresInstant.plus(allowedSkew).isBefore(now)) {
                return MessageSignatureResult.failure("Signature has expired");
            }
        }
        if (metadata.created != null
                && metadata.expires != null
                && metadata.expires < metadata.created) {
            return MessageSignatureResult.failure(
                    "Signature 'expires' parameter precedes the 'created' timestamp");
        }
        return MessageSignatureResult.success(null);
    }

    private void ensureMatchingLabels(
            Map<String, DictionaryEntry> signature,
            Map<String, DictionaryEntry> signatureInput) throws MessageSignatureException {
        for (String key : signature.keySet()) {
            if (!signatureInput.containsKey(key)) {
                throw new MessageSignatureException(
                        "Signature-Input header missing '" + key + "' entry");
            }
        }
        for (String key : signatureInput.keySet()) {
            if (!signature.containsKey(key)) {
                throw new MessageSignatureException(
                        "Signature header missing '" + key + "' entry");
            }
        }
    }

    private byte[] extractSignatureBytes(DictionaryEntry entry) throws MessageSignatureException {
        if (!(entry.getValue() instanceof Item)) {
            throw new MessageSignatureException("Signature item must be encoded as an item");
        }
        BareItem bareItem = ((Item) entry.getValue()).getBareItem();
        if (bareItem.getType() != BareItemType.BYTE_SEQUENCE) {
            throw new MessageSignatureException("Signature item is not encoded as a byte sequence");
        }
        byte[] signature = (byte[]) bareItem.getValue();
        return Arrays.copyOf(signature, signature.length);
    }

    private InnerList extractSignatureInput(DictionaryEntry entry) throws MessageSignatureException {
        SfvValue value = entry.getValue();
        if (!(value instanceof InnerList)) {
            throw new MessageSignatureException("Signature-Input entry must be an inner list");
        }
        return (InnerList) value;
    }

    private SignatureMetadata extractSignatureMetadata(Map<String, BareItem> parameters)
            throws MessageSignatureException {
        if (parameters == null || parameters.isEmpty()) {
            throw new MessageSignatureException("Signature parameters missing 'alg' entry");
        }
        String algorithm = null;
        Long created = null;
        Long expires = null;
        String keyId = null;
        String nonce = null;
        String tag = null;

        for (Map.Entry<String, BareItem> entry : parameters.entrySet()) {
            String key = entry.getKey();
            BareItem value = entry.getValue();
            switch (key) {
                case "alg":
                    algorithm = bareItemToString(value);
                    break;
                case "created":
                    created = bareItemToLong(value, "created");
                    break;
                case "expires":
                    expires = bareItemToLong(value, "expires");
                    break;
                case "keyid":
                    keyId = bareItemToString(value);
                    break;
                case "nonce":
                    nonce = bareItemToString(value);
                    break;
                case "tag":
                    tag = bareItemToString(value);
                    break;
                default:
                    throw new MessageSignatureException(
                            String.format("Unsupported signature parameter '%s'", key));
            }
        }

        if (!hasText(algorithm)) {
            throw new MessageSignatureException("Signature missing 'alg' parameter");
        }
        return new SignatureMetadata(algorithm, created, expires, keyId, nonce, tag);
    }

    private String buildCanonicalPayload(HttpServletRequest request, InnerList signatureInput)
            throws MessageSignatureException {
        List<String> lines = new ArrayList<>();
        for (Item component : signatureInput.getItems()) {
            String identifier = extractComponentIdentifier(component.getBareItem());
            String value = resolveComponentValue(request, identifier, component.getParameters());
            String label = SfvFormatter.serializeItem(component);
            lines.add(label + ": " + value);
        }
        String signatureParams = SfvFormatter.serializeItem(signatureInput);
        lines.add("\"@signature-params\": " + signatureParams);
        return String.join("\n", lines);
    }

    private String extractComponentIdentifier(BareItem item) throws MessageSignatureException {
        BareItemType type = item.getType();
        if (type == BareItemType.STRING || type == BareItemType.TOKEN) {
            return (String) item.getValue();
        }
        throw new MessageSignatureException(
                "Unsupported component identifier type '" + type + "'");
    }

    private String resolveComponentValue(
            HttpServletRequest request,
            String identifier,
            Map<String, BareItem> parameters) throws MessageSignatureException {
        if (identifier.startsWith("@")) {
            switch (identifier) {
                case "@method":
                    return request.getMethod();
                case "@target-uri":
                    return buildTargetUri(request);
                case "@authority":
                    return buildAuthority(request);
                case "@scheme":
                    return request.getScheme();
                case "@path":
                    return request.getRequestURI() == null ? "" : request.getRequestURI();
                case "@query":
                    return request.getQueryString() == null ? "" : request.getQueryString();
                case "@request-target":
                    return buildRequestTarget(request);
                case "@query-param":
                    return resolveQueryParam(request, parameters);
                default:
                    throw new MessageSignatureException(
                            "Unsupported derived component '" + identifier + "'");
            }
        }
        return resolveHeaderComponent(request, identifier, parameters);
    }

    private String resolveQueryParam(
            HttpServletRequest request,
            Map<String, BareItem> parameters) throws MessageSignatureException {
        if (parameters == null || !parameters.containsKey("name")) {
            throw new MessageSignatureException("@query-param requires a 'name' parameter");
        }
        String name = bareItemToString(parameters.get("name"));
        if (!hasText(name)) {
            throw new MessageSignatureException("@query-param 'name' parameter cannot be empty");
        }
        String[] values = request.getParameterValues(name);
        if (values == null || values.length == 0) {
            throw new MessageSignatureException(
                    "Missing query parameter '" + name + "' for @query-param component");
        }
        return String.join(",", values);
    }

    private String resolveHeaderComponent(
            HttpServletRequest request,
            String headerName,
            Map<String, BareItem> parameters) throws MessageSignatureException {
        List<String> values = Collections.list(request.getHeaders(headerName));
        if (values.isEmpty()) {
            throw new MessageSignatureException(
                    "Missing header '" + headerName + "' referenced in signature");
        }

        if (parameters != null && parameters.containsKey("sf") && isBooleanTrue(parameters.get("sf"))) {
            return serializeStructuredFieldHeader(headerName, values);
        }
        if (parameters != null && parameters.containsKey("key")) {
            String key = bareItemToString(parameters.get("key"));
            return extractDictionaryMember(headerName, values, key);
        }
        return combineHeaderValues(values);
    }

    private String serializeStructuredFieldHeader(String headerName, List<String> values)
            throws MessageSignatureException {
        String raw = combineHeaderValues(values);
        if (!hasText(raw)) {
            throw new MessageSignatureException(
                    "Missing header '" + headerName + "' referenced in signature");
        }
        try {
            LinkedHashMap<String, DictionaryEntry> dictionary = SfvParser.parseDictionary(raw);
            return SfvFormatter.serializeDictionary(dictionary);
        } catch (SfvParseException ignored) {
            // fallthrough
        }
        try {
            List<SfvValue> list = SfvParser.parseList(raw);
            return SfvFormatter.serializeList(list);
        } catch (SfvParseException ignored) {
            // fallthrough
        }
        try {
            SfvValue value = SfvParser.parseItem(raw);
            return SfvFormatter.serializeItem(value);
        } catch (SfvParseException ex) {
            throw new MessageSignatureException(
                    "Failed to parse header '" + headerName + "' as structured field: " + ex.getMessage());
        }
    }

    private String extractDictionaryMember(
            String headerName, List<String> values, String key) throws MessageSignatureException {
        if (!hasText(key)) {
            throw new MessageSignatureException("Header '" + headerName + "' dictionary key cannot be empty");
        }
        String raw = combineHeaderValues(values);
        try {
            LinkedHashMap<String, DictionaryEntry> dictionary = SfvParser.parseDictionary(raw);
            DictionaryEntry entry = dictionary.get(key);
            if (entry == null) {
                throw new MessageSignatureException(
                        "Header '" + headerName + "' dictionary missing key '" + key + "'");
            }
            return SfvFormatter.serializeItem(entry.getValue());
        } catch (SfvParseException ex) {
            throw new MessageSignatureException(
                    "Failed to parse header '" + headerName + "' as dictionary: " + ex.getMessage());
        }
    }

    private void verifyContentDigest(HttpServletRequest request) throws MessageSignatureException {
        List<String> digestHeaders = Collections.list(request.getHeaders(CONTENT_DIGEST_HEADER));
        if (digestHeaders.isEmpty()) {
            return;
        }
        String combined = combineHeaderValues(digestHeaders);
        if (!hasText(combined)) {
            return;
        }
        LinkedHashMap<String, DictionaryEntry> dictionary;
        try {
            dictionary = SfvParser.parseDictionary(combined);
        } catch (SfvParseException ex) {
            throw new MessageSignatureException(
                    "Failed to parse content-digest header: " + ex.getMessage());
        }

        byte[] body;
        try {
            body = getRequestBody(request);
        } catch (IOException ex) {
            throw new MessageSignatureException("Failed to buffer request body: " + ex.getMessage());
        }

        for (Map.Entry<String, DictionaryEntry> entry : dictionary.entrySet()) {
            String algorithm = entry.getKey();
            if (!SUPPORTED_CONTENT_DIGEST_ALGORITHMS.contains(algorithm.toLowerCase(Locale.ROOT))) {
                throw new MessageSignatureException(
                        "Unsupported content-digest algorithm '" + algorithm + "'");
            }
            byte[] expected = extractDigestBytes(entry.getValue());
            byte[] actual = computeDigest(algorithm, body);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new MessageSignatureException(
                        "Content digest verification failed for algorithm '" + algorithm + "'");
            }
        }
    }

    private byte[] extractDigestBytes(DictionaryEntry dictionaryEntry) throws MessageSignatureException {
        if (!(dictionaryEntry.getValue() instanceof Item)) {
            throw new MessageSignatureException("Content-Digest entry is not a valid item");
        }
        BareItem bareItem = ((Item) dictionaryEntry.getValue()).getBareItem();
        if (bareItem.getType() == BareItemType.BYTE_SEQUENCE) {
            byte[] bytes = (byte[]) bareItem.getValue();
            return Arrays.copyOf(bytes, bytes.length);
        }
        if (bareItem.getType() == BareItemType.STRING) {
            try {
                return Base64.getDecoder().decode((String) bareItem.getValue());
            } catch (IllegalArgumentException ex) {
                throw new MessageSignatureException("Invalid base64 content-digest value");
            }
        }
        throw new MessageSignatureException("Content-Digest entry is not a string or byte sequence");
    }

    private byte[] computeDigest(String algorithm, byte[] body) throws MessageSignatureException {
        String jcaAlgorithm = "sha-512".equalsIgnoreCase(algorithm) ? "SHA-512" : "SHA-256";
        try {
            MessageDigest digest = MessageDigest.getInstance(jcaAlgorithm);
            return digest.digest(body);
        } catch (GeneralSecurityException ex) {
            throw new MessageSignatureException("Failed to compute content digest: " + ex.getMessage());
        }
    }

    private byte[] getRequestBody(HttpServletRequest request) throws IOException {
        if (request instanceof CachedBodyHttpServletRequest) {
            return ((CachedBodyHttpServletRequest) request).getCachedBody();
        }
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ServletInputStream inputStream = request.getInputStream()) {
            byte[] data = new byte[2048];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private boolean verifySignature(
            String publicKeyBase64, byte[] signatureBytes, byte[] canonicalPayload) {
        try {
            byte[] publicKeyDer = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDer));

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(canonicalPayload);
            byte[] derSignature = convertIeeeP1363ToDer(signatureBytes);
            boolean verified = verifier.verify(derSignature);
            if (!verified) {
                LOGGER.debug(
                        "Approov message signing: signature verification failed for payload length {}",
                        canonicalPayload.length);
            }
            return verified;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            LOGGER.warn("Approov message signing: signature verification error - {}", ex.getMessage());
            return false;
        }
    }

    private byte[] convertIeeeP1363ToDer(byte[] signature) {
        if (signature == null || signature.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid ECDSA signature length");
        }
        int half = signature.length / 2;
        byte[] r = Arrays.copyOfRange(signature, 0, half);
        byte[] s = Arrays.copyOfRange(signature, half, signature.length);

        byte[] rDer = encodeDerInteger(r);
        byte[] sDer = encodeDerInteger(s);

        int totalLength = rDer.length + sDer.length;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x30);
        writeDerLength(output, totalLength);
        output.write(rDer, 0, rDer.length);
        output.write(sDer, 0, sDer.length);
        return output.toByteArray();
    }

    private byte[] encodeDerInteger(byte[] value) {
        int offset = 0;
        while (offset < value.length && value[offset] == 0) {
            offset++;
        }
        byte[] trimmed = Arrays.copyOfRange(value, offset, value.length);
        if (trimmed.length == 0) {
            trimmed = new byte[] {0};
        }
        if ((trimmed[0] & 0x80) != 0) {
            byte[] extended = new byte[trimmed.length + 1];
            System.arraycopy(trimmed, 0, extended, 1, trimmed.length);
            trimmed = extended;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x02);
        writeDerLength(output, trimmed.length);
        output.write(trimmed, 0, trimmed.length);
        return output.toByteArray();
    }

    private void writeDerLength(ByteArrayOutputStream output, int length) {
        if (length < 0x80) {
            output.write(length);
            return;
        }
        byte[] lengthBytes = intToBytes(length);
        output.write(0x80 | lengthBytes.length);
        output.write(lengthBytes, 0, lengthBytes.length);
    }

    private byte[] intToBytes(int value) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current = value;
        while (current > 0) {
            buffer.write(current & 0xFF);
            current >>>= 8;
        }
        byte[] raw = buffer.toByteArray();
        byte[] result = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            result[i] = raw[raw.length - 1 - i];
        }
        return result;
    }

    private String buildTargetUri(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        String scheme = request.getScheme();
        builder.append(scheme).append("://").append(buildAuthority(request));
        if (request.getRequestURI() != null) {
            builder.append(request.getRequestURI());
        }
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            builder.append('?').append(request.getQueryString());
        }
        return builder.toString();
    }

    private String buildAuthority(HttpServletRequest request) {
        String hostHeader = request.getHeader("Host");
        if (hasText(hostHeader)) {
            return hostHeader;
        }
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort =
                ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                        || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        if (defaultPort || port <= 0) {
            return host;
        }
        return host + ":" + port;
    }

    private String buildRequestTarget(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request.getRequestURI() != null) {
            builder.append(request.getRequestURI());
        }
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            builder.append('?').append(request.getQueryString());
        }
        return builder.toString();
    }

    private String combineHeaderValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private boolean isBooleanTrue(BareItem item) {
        return item != null
                && item.getType() == BareItemType.BOOLEAN
                && Boolean.TRUE.equals(item.getValue());
    }

    private String bareItemToString(BareItem item) throws MessageSignatureException {
        if (item == null) {
            return null;
        }
        BareItemType type = item.getType();
        switch (type) {
            case STRING:
            case TOKEN:
                return (String) item.getValue();
            default:
                throw new MessageSignatureException(
                        "Expected string parameter but received type '" + type + "'");
        }
    }

    private Long bareItemToLong(BareItem item, String name) throws MessageSignatureException {
        if (item == null) {
            return null;
        }
        if (item.getType() == BareItemType.INTEGER) {
            return (Long) item.getValue();
        }
        throw new MessageSignatureException(
                "Signature parameter '" + name + "' must be an integer");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class MessageSignatureResult {
        private final boolean success;
        private final String error;
        private final String canonicalMessage;

        private MessageSignatureResult(boolean success, String error, String canonicalMessage) {
            this.success = success;
            this.error = error;
            this.canonicalMessage = canonicalMessage;
        }

        static MessageSignatureResult success(String canonicalMessage) {
            return new MessageSignatureResult(true, null, canonicalMessage);
        }

        static MessageSignatureResult failure(String error) {
            return new MessageSignatureResult(false, error, null);
        }
    }

    private static final class SignatureMetadata {
        private final String algorithm;
        private final Long created;
        private final Long expires;
        private final String keyId;
        private final String nonce;
        private final String tag;

        private SignatureMetadata(
                String algorithm,
                Long created,
                Long expires,
                String keyId,
                String nonce,
                String tag) {
            this.algorithm = algorithm;
            this.created = created;
            this.expires = expires;
            this.keyId = keyId;
            this.nonce = nonce;
            this.tag = tag;
        }
    }

    private static final class MessageSignatureOptions {
        private final boolean requireCreated;
        private final boolean requireExpires;
        private final Duration maximumSignatureAge;
        private final Duration allowedClockSkew;

        private MessageSignatureOptions(
                boolean requireCreated,
                boolean requireExpires,
                Duration maximumSignatureAge,
                Duration allowedClockSkew) {
            this.requireCreated = requireCreated;
            this.requireExpires = requireExpires;
            this.maximumSignatureAge = maximumSignatureAge;
            this.allowedClockSkew = allowedClockSkew;
        }

        static MessageSignatureOptions fromEnvironment() {
            boolean requireCreated = getBooleanEnv("APPROOV_MSG_SIG_REQUIRE_CREATED", false);
            boolean requireExpires = getBooleanEnv("APPROOV_MSG_SIG_REQUIRE_EXPIRES", false);
            Duration maxAge = getDurationEnv("APPROOV_MSG_SIG_MAX_AGE_SECONDS");
            Duration clockSkew =
                    getDurationEnv("APPROOV_MSG_SIG_ALLOWED_SKEW_SECONDS");
            if (clockSkew == null) {
                clockSkew = Duration.ofMinutes(5);
            }
            return new MessageSignatureOptions(requireCreated, requireExpires, maxAge, clockSkew);
        }

        private static boolean getBooleanEnv(String name, boolean defaultValue) {
            String value = System.getenv(name);
            if (value == null) {
                return defaultValue;
            }
            return "true".equalsIgnoreCase(value.trim());
        }

        private static Duration getDurationEnv(String name) {
            String value = System.getenv(name);
            if (value == null) {
                return null;
            }
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds <= 0) {
                    return null;
                }
                return Duration.ofSeconds(seconds);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static final class MessageSignatureException extends Exception {
        MessageSignatureException(String message) {
            super(message);
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    ServletInputStream inputStream = request.getInputStream()) {
                byte[] data = new byte[2048];
                int read;
                while ((read = inputStream.read(data)) != -1) {
                    buffer.write(data, 0, read);
                }
                this.cachedBody = buffer.toByteArray();
            }
        }

        byte[] getCachedBody() {
            return Arrays.copyOf(cachedBody, cachedBody.length);
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(
                            getInputStream(),
                            StandardCharsets.UTF_8));
        }
    }

    private static final class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        CachedServletInputStream(byte[] data) {
            this.buffer = new ByteArrayInputStream(data);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("ReadListener not supported");
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }
}
