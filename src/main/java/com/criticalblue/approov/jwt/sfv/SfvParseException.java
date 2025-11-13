package com.criticalblue.approov.jwt.sfv;

/**
 * Exception thrown when Structured Field parsing fails.
 */
public class SfvParseException extends RuntimeException {

    public SfvParseException(String message) {
        super(message);
    }
}
