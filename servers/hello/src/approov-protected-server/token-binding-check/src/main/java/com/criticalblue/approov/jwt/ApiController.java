
package com.criticalblue.approov.jwt;

import com.criticalblue.approov.jwt.authentication.ApproovConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.GsonBuilderUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ApiController {

    private static Logger logger = LoggerFactory.getLogger(ApiController.class);

    public static boolean isTokenBindingEnebled = true;
    public static boolean FalseToTurnOffApproovEntirely = false;


    @GetMapping("/")
    public Map<String, Object> helloV1() {
        logger.info("Serving request for endpoint '/', that is protect by an Approov Token.");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "unprotected endpoint '/' & no Approov token required. & no Approov checks performed.");
        return response;
    }


    @GetMapping("/token-check")
    public Map<String, Object> tokenCheck() {
        logger.info("Serving request for '/token-check' (Approov Token check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "endpoint '/token-check' & Approov token valid & Approov checks performed");
        return response;
    }

    @GetMapping("/token-binding-check")
    public Map<String, Object> tokenBindingCheck(
            @RequestHeader(value = "Authorization", required = true) String authorizationHeader) {
        logger.info("Serving request for '/token-binding-check' (Approov Token check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "endpoint '/token-binding-check' & Approov token valid & Approov checks performed");
        System.out.println("isTokenBindingEnebled: " + isTokenBindingEnebled);
        response.put("isTokenBindingEnebled", isTokenBindingEnebled);

        return response;
    }

    @GetMapping("/token-binding-check-with-two-values")
    public Map<String, Object> tokenBindingCheckWithTwoValues(
            @RequestHeader(value = "Authorization", required = true) String authorizationHeader,
            @RequestHeader(value = "Content-Digest", required = true) String customHeader) {
        logger.info("Serving request for '/token-binding-check-with-two-values' (Approov Token Binding check with two values).");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "endpoint '/token-binding-check-with-two-values' & Approov token valid & Approov checks performed");
        response.put("authorizationHeaderPresent", authorizationHeader != null && !authorizationHeader.isEmpty());
        response.put("customHeaderPresent", customHeader != null && !customHeader.isEmpty());
        return response;
    }

        @GetMapping("/message-signing-check")
        public Map<String, Object> messageSigningCheck() {
        logger.info("Serving request for '/message-signing-check' (Approov Message Signing check).");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("details", "endpoint '/message-singing-check' & Approov token valid & Approov checks performed");
        return response;
    }
}
