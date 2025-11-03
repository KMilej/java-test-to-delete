package com.criticalblue.approov.jwt.authentication;

/**
 * Marker interface for Approov-specific exceptions exposing HTTP status information.
 */
public interface ApproovException {

    int getHttpStatusCode();
}
