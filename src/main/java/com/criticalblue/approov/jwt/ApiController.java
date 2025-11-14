package com.criticalblue.approov.jwt;

import com.criticalblue.approov.jwt.sfv.SfvFormatter;
import com.criticalblue.approov.jwt.sfv.SfvParseException;
import com.criticalblue.approov.jwt.sfv.SfvParser;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.DictionaryEntry;
import com.criticalblue.approov.jwt.sfv.StructuredFieldValues.SfvValue;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
    private static final AtomicBoolean APPROOV_ENABLED = new AtomicBoolean(true);
    private static final AtomicBoolean TOKEN_BINDING_ENABLED = new AtomicBoolean(true);
    private static final byte[] EC_P256_ALGORITHM_IDENTIFIER = new byte[] {
        0x30, 0x13,
        0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01,
        0x06, 0x08, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07
    };

    public static boolean isApproovEnabled() {
        return APPROOV_ENABLED.get();
    }

    public static boolean isTokenBindingEnabled() {
        return TOKEN_BINDING_ENABLED.get();
    }

    @GetMapping("/")
    public String index() {
        return "Welcome to the Approov JWT Demo API Server!";
    }

    @GetMapping("/approov-state")
    public ResponseEntity<Map<String, Object>> approovStatus() {
        boolean approovEnabled = APPROOV_ENABLED.get();
        Map<String, Object> response = newTokenBindingPayload();
        LOGGER.info(
                "Approov state check: approovEnabled={}, tokenBindingEnabled={}",
                approovEnabled,
                TOKEN_BINDING_ENABLED.get());
        return approovEnabled
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

    @PostMapping("/approov/enable")
    public Map<String, Object> approovEnable() {
        APPROOV_ENABLED.set(true);
        TOKEN_BINDING_ENABLED.set(true);
        return simpleFlagResponse("approovEnabled", true);
    }

    @PostMapping("/approov/disable")
    public Map<String, Object> approovDisable() {
        APPROOV_ENABLED.set(false);
        TOKEN_BINDING_ENABLED.set(false);
        return simpleFlagResponse("approovEnabled", false);
    }

    @PostMapping("/approov/toggle")
    public Map<String, Object> approovToggle() {
        boolean newValue = !APPROOV_ENABLED.get();
        APPROOV_ENABLED.set(newValue);
        return simpleFlagResponse("approovEnabled", newValue);
    }

    @PostMapping("/token-binding/enable")
    public Map<String, Object> tokenBindingEnable() {
        TOKEN_BINDING_ENABLED.set(true);
        return simpleFlagResponse("tokenBindingEnabled", true);
    }

    @PostMapping("/token-binding/disable")
    public Map<String, Object> tokenBindingDisable() {
        TOKEN_BINDING_ENABLED.set(false);
        return simpleFlagResponse("tokenBindingEnabled", false);
    }

    @GetMapping("/unprotected")
    public Map<String, Object> unprotectedEndpoint() {
        LOGGER.info("Serving request for '/unprotected' (unprotected).");
        Map<String, Object> response = newTokenBindingPayload();
        response.put(
                "details",
                "unprotected endpoint '/unprotected' & no Approov checks performed.");
        return response;
    }

    @GetMapping("/token-check")
    public Map<String, Object> tokenCheck() {
        LOGGER.info("Serving request for '/token-check'.");
        Map<String, Object> response =
                createApproovResponse(buildApproovDetails("/token-check"));
        return response;
    }

    @RequestMapping(value = "/token", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> tokenEndpoint(HttpServletRequest request) {
        LOGGER.info(
                "Serving request for '/token' with method={} query={}",
                request.getMethod(),
                request.getQueryString());
        Map<String, Object> response = createApproovResponse(buildApproovDetails("/token"));
        response.put("method", request.getMethod());
        response.put("query", request.getQueryString());
        return response;
    }

    @GetMapping("/token-binding-1")
    public Map<String, Object> tokenBindingCheck(
            @RequestHeader(value = "Authorization", required = false)
            String authorizationHeader) {
        LOGGER.info("Serving request for '/token-binding-1'.");
        Map<String, Object> response =
                createTokenBindingResponse(buildApproovDetails("/token-binding-1"));
        response.put("authorizationHeaderPresent", hasText(authorizationHeader));
        return response;
    }

    @GetMapping("/token-binding-2")
    public Map<String, Object> tokenBindingCheckWithTwoValues(
            @RequestHeader(value = "Authorization", required = false)
            String authorizationHeader,
            @RequestHeader(value = "Content-Digest", required = false)
            String customHeader) {
        LOGGER.info("Serving request for '/token-binding-2'.");
        Map<String, Object> response =
                createTokenBindingResponse(buildApproovDetails("/token-binding-2"));
        response.put("authorizationHeaderPresent", hasText(authorizationHeader));
        response.put("customHeaderPresent", hasText(customHeader));
        return response;
    }

    @GetMapping("/message-signing-check")
    public ResponseEntity<Map<String, Object>> messageSigningCheck() {
        LOGGER.info("Serving request for '/message-signing-check'.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("details", buildApproovDetails("/message-signing-check"));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/sfv_test")
    public ResponseEntity<String> structuredFieldTest(
            @RequestHeader(value = "sfvt", required = false) String typeHeader,
            @RequestHeader(value = "sfv", required = false) List<String> sfvHeaderValues) {
        String combinedValue = combineHeaderValues(sfvHeaderValues);
        if (!hasText(typeHeader) || !hasText(combinedValue)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing sfv or sfvt header");
        }

        String upperType = typeHeader.trim().toUpperCase(Locale.ROOT);
        try {
            String serialized;
            switch (upperType) {
                case "ITEM":
                    SfvValue value = SfvParser.parseItem(combinedValue);
                    serialized = SfvFormatter.serializeItem(value);
                    break;
                case "LIST":
                    List<SfvValue> list = SfvParser.parseList(combinedValue);
                    serialized = SfvFormatter.serializeList(list);
                    break;
                case "DICTIONARY":
                    LinkedHashMap<String, DictionaryEntry> dictionary =
                            SfvParser.parseDictionary(combinedValue);
                    serialized = SfvFormatter.serializeDictionary(dictionary);
                    break;
                default:
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Unsupported sfvt value '" + upperType + "'");
            }

            if (!serialized.equals(combinedValue)) {
                return structuredFieldFailure(
                        String.format(
                                "Serialized object does not match original: %s != %s",
                                serialized,
                                combinedValue));
            }

            return ResponseEntity.ok("SFV roundtrip OK");
        } catch (SfvParseException ex) {
            return structuredFieldFailure(ex.getMessage());
        }
    }

        @GetMapping("/ipk_test")
    public ResponseEntity<String> ipkTest(
            @RequestHeader(value = "ipk", required = false) String ipkHeader) {
        if (!hasText(ipkHeader)) {
            try {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair keyPair = kpg.generateKeyPair();
                String privateKeyBase64 =
                        Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
                String publicKeyBase64 =
                        Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
                LOGGER.debug(
                        "Generated EC key pair for testing. Private DER (b64)={} Public DER (b64)={}",
                        privateKeyBase64,
                        publicKeyBase64);
            } catch (GeneralSecurityException ex) {
                LOGGER.warn("Failed to generate EC key pair for /ipk_test", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate key pair");
            }
            return ResponseEntity.ok("No IPK header provided");
        }
        try {
            byte[] publicKeyDer = Base64.getDecoder().decode(ipkHeader);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDer));
            return ResponseEntity.ok("IPK roundtrip OK");
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            LOGGER.warn("Failed to import IPK header - {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Failed: failed to create public key");
        }
    }
    /*
     * WARNING: This endpoint is only for controlled testing. Never expose real keys via
     * plaintext headers in production as they can be intercepted, logged, or cached.
     */
    @GetMapping("/ipk_message_sign_test")
    public ResponseEntity<String> ipkMessageSignTest(
            @RequestHeader(value = "private-key", required = false) String privateKeyBase64,
            @RequestHeader(value = "msg", required = false) String messageBase64) {
        if (!hasText(privateKeyBase64) || !hasText(messageBase64)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing private-key or msg header");
        }
        try {
            byte[] privateKeyDer = Base64.getDecoder().decode(privateKeyBase64);
            byte[] messageBytes = Base64.getDecoder().decode(messageBase64);
            PrivateKey privateKey = importEcPrivateKey(privateKeyDer);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(messageBytes);
            byte[] derSignature = signature.sign();
            byte[] ieeeSignature = convertDerToIeeeP1363(derSignature, 32);
            String encodedSignature = Base64.getEncoder().encodeToString(ieeeSignature);
            return ResponseEntity.ok(encodedSignature);
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Invalid input for signing - {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Failed to generate signature");
        } catch (GeneralSecurityException ex) {
            LOGGER.error("Cryptographic failure during signing", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Failed to generate signature");
        }
    }

    private PrivateKey importEcPrivateKey(byte[] privateKeyDer) throws GeneralSecurityException {
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        try {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
        } catch (InvalidKeySpecException ex) {
            byte[] wrapped = wrapSec1EcPrivateKey(privateKeyDer);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(wrapped));
        }
    }

    private byte[] wrapSec1EcPrivateKey(byte[] sec1Der) throws GeneralSecurityException {
        if (sec1Der == null || sec1Der.length == 0 || sec1Der[0] != 0x30) {
            throw new GeneralSecurityException("Unsupported EC private key format");
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // version = 0
        body.write(0x02);
        body.write(0x01);
        body.write(0x00);
        // algorithm identifier for id-ecPublicKey + secp256r1
        body.writeBytes(EC_P256_ALGORITHM_IDENTIFIER);
        // private key octet string
        body.write(0x04);
        writeDerLength(body, sec1Der.length);
        body.writeBytes(sec1Der);

        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(0x30);
        writeDerLength(outer, body.size());
        outer.writeBytes(body.toByteArray());
        return outer.toByteArray();
    }

    private void writeDerLength(ByteArrayOutputStream output, int length) {
        if (length < 0x80) {
            output.write(length);
            return;
        }
        int temp = length;
        int numBytes = 0;
        byte[] buffer = new byte[4];
        while (temp > 0) {
            buffer[3 - numBytes] = (byte) (temp & 0xFF);
            temp >>>= 8;
            numBytes++;
        }
        output.write(0x80 | numBytes);
        output.write(buffer, 4 - numBytes, numBytes);
    }

    private byte[] convertDerToIeeeP1363(byte[] derSignature, int coordinateSize)
            throws GeneralSecurityException {
        if (derSignature == null || derSignature.length == 0 || derSignature[0] != 0x30) {
            throw new GeneralSecurityException("Invalid DER encoded signature");
        }
        int offset = 1;
        int[] lengthResult = readDerLength(derSignature, offset);
        int sequenceLength = lengthResult[0];
        offset = lengthResult[1];
        if (offset + sequenceLength > derSignature.length) {
            throw new GeneralSecurityException("DER signature truncated");
        }

        if (derSignature[offset++] != 0x02) {
            throw new GeneralSecurityException("DER signature missing R component");
        }
        lengthResult = readDerLength(derSignature, offset);
        int rLength = lengthResult[0];
        offset = lengthResult[1];
        byte[] r = Arrays.copyOfRange(derSignature, offset, offset + rLength);
        offset += rLength;

        if (derSignature[offset++] != 0x02) {
            throw new GeneralSecurityException("DER signature missing S component");
        }
        lengthResult = readDerLength(derSignature, offset);
        int sLength = lengthResult[0];
        offset = lengthResult[1];
        byte[] s = Arrays.copyOfRange(derSignature, offset, offset + sLength);

        byte[] ieee = new byte[coordinateSize * 2];
        writeIntegerComponent(ieee, 0, coordinateSize, r);
        writeIntegerComponent(ieee, coordinateSize, coordinateSize, s);
        return ieee;
    }

    private int[] readDerLength(byte[] data, int offset) throws GeneralSecurityException {
        if (offset >= data.length) {
            throw new GeneralSecurityException("Invalid DER length encoding");
        }
        int length = data[offset++] & 0xFF;
        if ((length & 0x80) == 0) {
            return new int[] {length, offset};
        }
        int numBytes = length & 0x7F;
        if (numBytes == 0 || numBytes > 4 || offset + numBytes > data.length) {
            throw new GeneralSecurityException("Invalid DER length encoding");
        }
        int value = 0;
        for (int i = 0; i < numBytes; i++) {
            value = (value << 8) | (data[offset++] & 0xFF);
        }
        return new int[] {value, offset};
    }

    private void writeIntegerComponent(
            byte[] target, int offset, int size, byte[] value) throws GeneralSecurityException {
        int valueOffset = 0;
        while (valueOffset < value.length && value[valueOffset] == 0) {
            valueOffset++;
        }
        int valueLength = value.length - valueOffset;
        if (valueLength > size) {
            throw new GeneralSecurityException("ECDSA component does not fit target size");
        }
        int padding = size - valueLength;
        Arrays.fill(target, offset, offset + padding, (byte) 0);
        System.arraycopy(value, valueOffset, target, offset + padding, valueLength);
    }

    private static Map<String, Object> simpleFlagResponse(String key, boolean value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(key, value);
        return response;
    }

    private ResponseEntity<String> structuredFieldFailure(String message) {
        LOGGER.debug("Structured field roundtrip failure - {}", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Failed SFV roundtrip");
    }

    private static String combineHeaderValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.get(0);
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

    private static Map<String, Object> newStatePayload() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approovEnabled", APPROOV_ENABLED.get());
        return response;
    }

    private static Map<String, Object> newTokenBindingPayload() {
        Map<String, Object> response = newStatePayload();
        response.put("tokenBindingEnabled", TOKEN_BINDING_ENABLED.get());
        return response;
    }

    private static Map<String, Object> createApproovResponse(String details) {
        Map<String, Object> response = newStatePayload();
        response.put("details", details);
        return response;
    }

    private static Map<String, Object> createTokenBindingResponse(String details) {
        Map<String, Object> response = newTokenBindingPayload();
        response.put("details", details);
        return response;
    }

    private static String buildApproovDetails(String endpoint) {
        if (APPROOV_ENABLED.get()) {
            return String.format(
                    "endpoint '%s' & Approov token valid & Approov checks performed",
                    endpoint);
        }
        return String.format(
                "endpoint '%s' & Approov DISABLED — no checks performed", endpoint);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
