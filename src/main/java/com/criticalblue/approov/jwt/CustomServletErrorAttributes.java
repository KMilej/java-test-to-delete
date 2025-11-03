package com.criticalblue.approov.jwt;

import java.util.Arrays;
import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

@Component
public class CustomServletErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {

        Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);

        // Remove from response in order to make the response comply with the Hello API specification
        errorAttributes.keySet().removeAll(Arrays.asList(
                "timestamp", "message", "path", "error", "trace", "status"));

        return errorAttributes;
    }
}
