package com.criticalblue.approov.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    public static boolean isTokenBindingEnebled = true;

    // --- Global flags (thread-safe) ---
    public static final AtomicBoolean APPROOV_ENABLED = new AtomicBoolean(true);   // default: ON
    private static final AtomicBoolean TOKEN_BINDING_ENABLED = new AtomicBoolean(true);

    // Accessors for other classes (e.g., Security layer)
    public static boolean isApproovEnabled() {
        return APPROOV_ENABLED.get();
    }

    public static boolean isTokenBindingEnabled() {
        return TOKEN_BINDING_ENABLED.get();
    }

    // =========================
    //        Admin Endpoints
    // =========================

    @GetMapping("/admin/approov/status")
    public Map<String, Object> approovStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approovEnabled", APPROOV_ENABLED.get());
        response.put("tokenBindingEnabled", TOKEN_BINDING_ENABLED.get());
        return response;
    }

    @PostMapping("/admin/approov/enable")
    public Map<String, Object> approovEnable() {
        Map<String, Object> response = new LinkedHashMap<>();
        APPROOV_ENABLED.set(true);
        response.put("approovEnabled", true);
        return response;
    }

    @PostMapping("/admin/approov/disable")
    public Map<String, Object> approovDisable() {
        Map<String, Object> response = new LinkedHashMap<>();
        APPROOV_ENABLED.set(false);
        response.put("approovEnabled", false);
        return response;
    }

    @PostMapping("/admin/approov/toggle")
    public Map<String, Object> approovToggle() {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean newVal = !APPROOV_ENABLED.get();
        APPROOV_ENABLED.set(newVal);
        response.put("approovEnabled", newVal);
        return response;
    }

    @PostMapping("/admin/token-binding/enable")
    public Map<String, Object> tbEnable() {
        Map<String, Object> response = new LinkedHashMap<>();
        TOKEN_BINDING_ENABLED.set(true);
        response.put("tokenBindingEnabled", true);
        return response;
    }

    @PostMapping("/admin/token-binding/disable")
    public Map<String, Object> tbDisable() {
        Map<String, Object> response = new LinkedHashMap<>();
        TOKEN_BINDING_ENABLED.set(false);
        response.put("tokenBindingEnabled", false);
        return response;
    }

    // =========================
    //        API Endpoints
    // =========================

    @GetMapping("/")
    public Map<String, Object> helloV1() {
        logger.info("Serving request for '/' (unprotected).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "unprotected endpoint '/' & no Approov checks performed.");
        response.put("approovEnabled", APPROOV_ENABLED.get());
        response.put("tokenBindingEnabled", TOKEN_BINDING_ENABLED.get());
        return response;
    }

    @GetMapping("/token-check")
    public Map<String, Object> tokenCheck() {
        logger.info("Serving request for '/token-check'.");
        Map<String, Object> response = new LinkedHashMap<>();
        if (APPROOV_ENABLED.get()) {
            response.put("details", "endpoint '/token-check' & Approov token valid & Approov checks performed");
        } else {
            response.put("details", "endpoint '/token-check' & Approov DISABLED — no checks performed");
        }
        response.put("approovEnabled", APPROOV_ENABLED.get());
        return response;
    }

    @GetMapping("/token-binding-check")
    public Map<String, Object> tokenBindingCheck(
            @RequestHeader(value = "Authorization", required = true) String authorizationHeader) {
        logger.info("Serving request for '/token-binding-check'.");
        Map<String, Object> response = new LinkedHashMap<>();
        if (APPROOV_ENABLED.get()) {
            response.put("details", "endpoint '/token-binding-check' & Approov token valid & Approov checks performed");
        } else {
            response.put("details", "endpoint '/token-binding-check' & Approov DISABLED — no checks performed");
        }
        response.put("tokenBindingEnabled", TOKEN_BINDING_ENABLED.get());
        response.put("authorizationHeaderPresent",
                authorizationHeader != null && !authorizationHeader.isEmpty());
        return response;
    }

    @GetMapping("/token-binding-check-with-two-values")
    public Map<String, Object> tokenBindingCheckWithTwoValues(
            @RequestHeader(value = "Authorization", required = true) String authorizationHeader,
            @RequestHeader(value = "Content-Digest", required = true) String customHeader) {
        logger.info("Serving request for '/token-binding-check-with-two-values'.");
        Map<String, Object> response = new LinkedHashMap<>();
        if (APPROOV_ENABLED.get()) {
            response.put("details", "endpoint '/token-binding-check-with-two-values' & Approov token valid & Approov checks performed");
        } else {
            response.put("details", "endpoint '/token-binding-check-with-two-values' & Approov DISABLED — no checks performed");
        }
        response.put("tokenBindingEnabled", TOKEN_BINDING_ENABLED.get());
        response.put("authorizationHeaderPresent",
                authorizationHeader != null && !authorizationHeader.isEmpty());
        response.put("customHeaderPresent",
                customHeader != null && !customHeader.isEmpty());
        return response;
    }

    @GetMapping("/message-signing-check")
    public ResponseEntity<Map<String, Object>> messageSigningCheck() {
        logger.info("Serving request for '/message-signing-check'.");
        Map<String, Object> body = new LinkedHashMap<>();
        if (APPROOV_ENABLED.get()) {
            body.put("details", "endpoint '/message-signing-check' & Approov token valid & Approov checks performed");
        } else {
            body.put("details", "endpoint '/message-signing-check' & Approov DISABLED — no checks performed");
        }
        return ResponseEntity.ok(body);
    }
}
