package com.criticalblue.approov.jwt.authentication;

import org.springframework.http.HttpStatus;

/**
 * The Approov configuration that is built from the .env file in the root of the package.
 */
public final class ApproovConfig {

    private static final ApproovConfig INSTANCE = new ApproovConfig();
    private static final String DEFAULT_APPROOV_HEADER_NAME = "Approov-Token";

    private final String approovBase64Secret;
    private final String approovTokenBindingHeaderName;

    private ApproovConfig() {
        this.approovBase64Secret = loadApproovBase64Secret();
        this.approovTokenBindingHeaderName =
                loadStringFromEnv("APPROOV_TOKEN_BINDING_HEADER_NAME", "Authorization");
    }

    public static ApproovConfig getInstance() {
        return INSTANCE;
    }

    String getApproovHeaderName() {
        return DEFAULT_APPROOV_HEADER_NAME;
    }

    String getApproovTokenBindingHeaderName() {
        return approovTokenBindingHeaderName;
    }

    String getApproovBase64Secret() {
        return approovBase64Secret;
    }

    private String loadApproovBase64Secret() {
        String secret = System.getenv("APPROOV_BASE64_SECRET");
        if (secret == null) {
            throw new ApproovAuthenticationException(
                    "Cannot retrieve APPROOV_BASE64_SECRET from the environment.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return secret;
    }

    private String loadStringFromEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            return defaultValue;
        }
        return value.trim();
    }
}
