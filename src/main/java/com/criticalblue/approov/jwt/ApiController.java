package com.criticalblue.approov.jwt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
    private static final AtomicBoolean APPROOV_ENABLED = new AtomicBoolean(true);
    private static final AtomicBoolean TOKEN_BINDING_ENABLED = new AtomicBoolean(true);

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

    private static Map<String, Object> simpleFlagResponse(String key, boolean value) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(key, value);
        return response;
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
