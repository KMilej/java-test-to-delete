package com.criticalblue.approov.jwt.authentication;

import org.springframework.security.core.AuthenticationException;

/**
 * Custom exception for failures when verifying the Approov token signature and
 * expiration time.
 *
 * @see ApproovAuthentication
 */
class ApproovAuthenticationException extends AuthenticationException implements ApproovException {

    private final int httpStatusCode;

    public ApproovAuthenticationException(String msg, int httpStatusCode) {
        super(msg);
        this.httpStatusCode = httpStatusCode;
    }

    @Override
    public int getHttpStatusCode() {
        return this.httpStatusCode;
    }
}
