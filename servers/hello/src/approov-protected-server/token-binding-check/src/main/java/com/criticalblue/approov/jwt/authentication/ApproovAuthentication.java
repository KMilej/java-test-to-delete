package com.criticalblue.approov.jwt.authentication;

import java.util.Collection;
import java.util.Collections;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;

public class ApproovAuthentication implements ApproovJwtAuthentication {

    private static Logger logger = LoggerFactory.getLogger(ApproovAuthentication.class);

    private final ApproovTokenBindingAuthentication approovPayload = new ApproovTokenBindingAuthentication();

    private final ApproovConfig approovConfig;

    private Claims approovTokenPayloadClaims;

    private String tokenBindingHeader;

    private String approovToken;

    private boolean isAuthenticated = false;

    private boolean validTokenBinding;

    /** NEW: whether to enforce token binding for THIS request */
    private final boolean enforceBinding;

    /** Without binding (default) */
    public ApproovAuthentication(ApproovConfig approovConfig, String approovToken) {
        this(approovConfig, approovToken, null, false);
    }

    /** With binding (enabled by default when a header is provided) */
    public ApproovAuthentication(ApproovConfig approovConfig, String approovToken, String tokenBindingHeader) {
        this(approovConfig, approovToken, tokenBindingHeader, true);
    }

    /** Main constructor – explicitly controls enforceBinding */
    public ApproovAuthentication(ApproovConfig approovConfig, String approovToken,
                                 String tokenBindingHeader, boolean enforceBinding) {
        this.approovConfig = approovConfig;
        this.approovToken = approovToken;
        this.tokenBindingHeader = tokenBindingHeader;
        this.enforceBinding = enforceBinding;
    }
    /**
     * Verifies the Approov token and, when enforceBinding == true,
     * also checks the token binding.
     *
     * @param approovSecret The Approov secret for verifying the token signature.
     * @throws ApproovAuthenticationException When the token is invalid or, when enforceBinding == true,
     *                                        the token binding check fails.
     */

    @Override
    public void verifyApproovToken(byte[] approovSecret) throws ApproovAuthenticationException {

        if (approovSecret == null) {
            throw new ApproovAuthenticationException("The Approov secret is null.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        if (approovToken == null) {
            throw new ApproovAuthenticationException("The Approov token is null.", HttpStatus.FORBIDDEN.value());
        }

        approovToken = approovToken.trim();

        if (approovToken.equals("")) {
            throw new ApproovAuthenticationException("The Approov token is empty.", HttpStatus.BAD_REQUEST.value());
        }

        try {
            approovTokenPayloadClaims = Jwts.parser()
                    .setSigningKey(approovSecret)
                    .parseClaimsJws(approovToken)
                    .getBody();

            logger.info("Request approved with a valid Approov token.");
        } catch (JwtException e) {
            String message = "Request with an invalid Approov token: " + e.getMessage();
            throw new ApproovAuthenticationException(message, HttpStatus.UNAUTHORIZED.value());
        }

        // ── KEY: check token binding only when enforceBinding == true
        if (enforceBinding) {
            if (tokenBindingHeader == null || tokenBindingHeader.isEmpty()) {
                throw new ApproovAuthenticationException(
                        "Token binding enabled for this endpoint, but the binding header is missing.",
                        HttpStatus.UNAUTHORIZED.value());
            }
            validTokenBinding = approovPayload.checkClaimMatchesFor(
                    tokenBindingHeader, approovTokenPayloadClaims, approovConfig);

            if (!validTokenBinding) {
                throw new ApproovAuthenticationException(
                        "Approov token binding mismatch.", HttpStatus.UNAUTHORIZED.value());
            }
        } else {
            validTokenBinding = true; // binding disabled for this endpoint
        }

        isAuthenticated = true;
    }


    @Override
    public Claims getApproovTokenPayloadClaims() { return approovTokenPayloadClaims; }

    @Override
    public boolean isValidTokenBinding() { return validTokenBinding; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return Collections.emptyList(); }

    @Override
    public Object getCredentials() { return approovToken; }

    @Override
    public Object getDetails() { return approovTokenPayloadClaims; }

    @Override
    public Object getPrincipal() { return null; }

    @Override
    public boolean isAuthenticated() { return isAuthenticated; }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new ApproovAuthenticationException(
                    "A new Approov Authentication instance needs to be created to set this.isAuthenticated.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    public String getName() { return null; }
}
