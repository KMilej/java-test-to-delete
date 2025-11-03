package com.criticalblue.approov.jwt.authentication;

import java.util.Collection;
import java.util.Collections;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;

public class ApproovAuthentication implements ApproovJwtAuthentication {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApproovAuthentication.class);

    private final ApproovTokenBindingAuthentication tokenBindingValidator =
            new ApproovTokenBindingAuthentication();
    private final String tokenBindingHeader;
    private final boolean enforceBinding;

    private Claims approovTokenPayloadClaims;
    private String approovToken;
    private boolean authenticated;
    private boolean validTokenBinding;

    @Deprecated(forRemoval = false)
    public ApproovAuthentication(
            ApproovConfig approovConfig,
            String approovToken,
            String tokenBindingHeader,
            boolean enforceBinding) {
        this(approovToken, tokenBindingHeader, enforceBinding);
    }

    public ApproovAuthentication(
            String approovToken, String tokenBindingHeader, boolean enforceBinding) {
        this.approovToken = approovToken;
        this.tokenBindingHeader = tokenBindingHeader;
        this.enforceBinding = enforceBinding;
    }

    /**
     * Verifies the Approov token and, when {@code enforceBinding == true}, also checks the token binding.
     *
     * @param approovSecret The Approov secret for verifying the token signature.
     * @throws ApproovAuthenticationException When the token is invalid or, when {@code enforceBinding == true},
     *                                        the token binding check fails.
     */
    @Override
    public void verifyApproovToken(byte[] approovSecret) throws ApproovAuthenticationException {
        validateSecret(approovSecret);
        approovToken = sanitizeToken(approovToken);
        parseTokenClaims(approovSecret);

        if (enforceBinding) {
            validateTokenBinding();
        } else {
            validTokenBinding = true;
        }

        authenticated = true;
    }

    @Override
    public Claims getApproovTokenPayloadClaims() {
        return approovTokenPayloadClaims;
    }

    @Override
    public boolean isValidTokenBinding() {
        return validTokenBinding;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return approovToken;
    }

    @Override
    public Object getDetails() {
        return approovTokenPayloadClaims;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new ApproovAuthenticationException(
                    "A new Approov Authentication instance needs to be created to set this.isAuthenticated.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    public String getName() {
        return null;
    }

    private void validateSecret(byte[] approovSecret) {
        if (approovSecret != null) {
            return;
        }
        throw new ApproovAuthenticationException(
                "The Approov secret is null.", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    private String sanitizeToken(String candidateToken) {
        if (candidateToken == null) {
            throw new ApproovAuthenticationException(
                    "The Approov token is null.", HttpStatus.FORBIDDEN.value());
        }

        String trimmedToken = candidateToken.trim();
        if (!trimmedToken.isEmpty()) {
            return trimmedToken;
        }

        throw new ApproovAuthenticationException(
                "The Approov token is empty.", HttpStatus.BAD_REQUEST.value());
    }

    private void parseTokenClaims(byte[] approovSecret) {
        try {
            approovTokenPayloadClaims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(approovSecret))
                    .build()
                    .parseClaimsJws(approovToken)
                    .getBody();

            LOGGER.info("Request approved with a valid Approov token.");
        } catch (JwtException e) {
            String message = "Request with an invalid Approov token: " + e.getMessage();
            throw new ApproovAuthenticationException(message, HttpStatus.UNAUTHORIZED.value());
        }
    }

    private void validateTokenBinding() {
        if (tokenBindingHeader == null || tokenBindingHeader.trim().isEmpty()) {
            throw new ApproovAuthenticationException(
                    "Token binding enabled for this endpoint, but the binding header is missing.",
                    HttpStatus.UNAUTHORIZED.value());
        }

        String bindingHeader = tokenBindingHeader.trim();
        validTokenBinding =
                tokenBindingValidator.checkClaimMatchesFor(bindingHeader, approovTokenPayloadClaims);
    }
}
