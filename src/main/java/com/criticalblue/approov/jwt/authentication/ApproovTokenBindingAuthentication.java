package com.criticalblue.approov.jwt.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.jsonwebtoken.Claims;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;

class ApproovTokenBindingAuthentication {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApproovTokenBindingAuthentication.class);

    /**
     * Checks the value in the key {@code pay} of an Approov token matches the token binding header, that by default is
     * the value for the {@code Authorization} header.
     *
     * @param tokenBindingHeader        Extracted from the header that carries the binding value.
     * @param approovTokenPayloadClaims Extracted from the already verified Approov token.
     * @return {@code true} if the token binding header matches the {@code pay} claim; otherwise throws an exception.
     */
    boolean checkClaimMatchesFor(String tokenBindingHeader, Claims approovTokenPayloadClaims) {
        if (tokenBindingHeader == null) {
            throw new ApproovTokenBindingAuthenticationException(
                    "The token binding header value is null.", HttpStatus.BAD_REQUEST.value());
        }

        String expectedBinding = extractApproovTokenBindingClaim(approovTokenPayloadClaims);
        boolean isValidTokenBinding =
                hashBase64Encoded(tokenBindingHeader).equals(expectedBinding);

        if (isValidTokenBinding) {
            LOGGER.info("Request approved with a valid token binding in the Approov token.");
            return true;
        }

        throw new ApproovTokenBindingAuthenticationException(
                "The token binding header does not match the key `pay` in the Approov token.",
                HttpStatus.UNAUTHORIZED.value());
    }

    private String extractApproovTokenBindingClaim(Claims approovTokenPayloadClaims) {
        if (approovTokenPayloadClaims == null) {
            throw new ApproovTokenBindingAuthenticationException(
                    "Approov token payload is null.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        if (!approovTokenPayloadClaims.containsKey("pay")) {
            throw new ApproovTokenBindingAuthenticationException(
                    "The key `pay`, for the token binding, is missing in the Approov token payload.",
                    HttpStatus.BAD_REQUEST.value());
        }

        Object claimValue = approovTokenPayloadClaims.get("pay");
        if (claimValue == null) {
            throw new ApproovTokenBindingAuthenticationException(
                    "The token binding in the Approov token is null.", HttpStatus.BAD_REQUEST.value());
        }

        String binding = claimValue.toString().trim();
        if (!binding.isEmpty()) {
            return binding;
        }

        throw new ApproovTokenBindingAuthenticationException(
                "The token binding in the Approov token is empty.", HttpStatus.BAD_REQUEST.value());
    }

    private String hashBase64Encoded(String value) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AuthenticationServiceException(e.getMessage());
        }

        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBase64String(hash);
    }
}
