
package com.criticalblue.approov.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApiController {

    private static Logger logger = LoggerFactory.getLogger(ApiController.class);

    @GetMapping("/")
    public Map<String, Object> helloV1() {

        logger.info("Serving request for endpoint '/', that is protect by an Approov Token.");

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("message", "Hello, World!");

        return response;
    }
    @GetMapping("/token-check")
    public Map<String, Object> tokenCheck() {
        logger.info("Serving request for '/token-check' (Approov Token check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "This is the token-check endpoint.");
        response.put("detail", "Access permitted only when Approov Token is valid (handled by security filter).");
        return response;
    }

    @GetMapping("/token-binding-check")
    public Map<String, Object> tokenBindingCheck(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        logger.info("Serving request for '/token-binding-check' (Approov Token Binding check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "This is the token-binding-check endpoint.");
        response.put("hint", "Server-side filter verifies the Approov token's binding to the chosen header value.");
        response.put("authorizationHeaderPresent", authorizationHeader != null && !authorizationHeader.isEmpty());
        return response;
    }

    @GetMapping("/message-signing-check")
    public Map<String, Object> messageSigningCheck() {
        logger.info("Serving request for '/message-signing-check' (Approov Message Signing check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "This is the message-signing-check endpoint.");
        response.put("detail", "Server-side filter validates the Approov signature for the request.");
        return response;
    }
}
