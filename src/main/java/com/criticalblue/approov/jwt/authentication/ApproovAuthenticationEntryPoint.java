package com.criticalblue.approov.jwt.authentication;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Handles authentication failures raised during the Approov token verification process.
 */
public class ApproovAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApproovAuthenticationEntryPoint.class);

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {

        int httpStatusCode = resolveHttpStatus(authException);
        logFailure(httpStatusCode, authException);
        response.sendError(httpStatusCode);
    }

    private static int resolveHttpStatus(AuthenticationException authException) {
        if (authException instanceof ApproovException) {
            return ((ApproovException) authException).getHttpStatusCode();
        }
        return HttpStatus.BAD_REQUEST.value();
    }

    private static void logFailure(int httpStatusCode, AuthenticationException authException) {
        StackTraceElement[] stackTrace = authException.getStackTrace();
        String origin = stackTrace.length > 0 ? stackTrace[0].toString() : "unknown";
        LOGGER.error(
                "{} | {} | {} | Origin: {}",
                HttpStatus.valueOf(httpStatusCode),
                authException.getClass().getName(),
                authException.getMessage(),
                origin);
    }
}
