package com.criticalblue.approov.jwt.authentication;

import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.criticalblue.approov.jwt.ApiController;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

public class ApproovSecurityContextRepository implements SecurityContextRepository {

    private final ApproovConfig approovConfig;

    public ApproovSecurityContextRepository(ApproovConfig approovConfig) {
        this.approovConfig = approovConfig;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        HttpServletRequest request =
                ApproovMessageSigningValidator.ensureCachedRequest(requestResponseHolder.getRequest());
        requestResponseHolder.setRequest(request);
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        if (!ApiController.isApproovEnabled()) {
            context.setAuthentication(disabledAuthentication());
            return context;
        }

        String approovToken = request.getHeader(approovConfig.getApproovHeaderName());
        if (!hasText(approovToken)) {
            return context;
        }

        Authentication approovAuthentication =
                buildAuthentication(request, approovToken.trim());
        context.setAuthentication(approovAuthentication);
        return context;
    }

    @Override
    public void saveContext(
            SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        // Stateless authentication; nothing to persist.
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return !ApiController.isApproovEnabled()
                || hasText(request.getHeader(approovConfig.getApproovHeaderName()));
    }

    private Authentication buildAuthentication(HttpServletRequest request, String approovToken) {
        String path = request.getRequestURI();
        if (path == null) {
            path = "";
        }

        switch (path) {
            case "/unprotected":
            case "/token-check":
            case "/token":
                return new ApproovAuthentication(request, approovToken, null, false);
            case "/token-binding-1":
                return new ApproovAuthentication(
                        request, approovToken, getSingleBindingValue(request), true);
            case "/token-binding-2":
                return new ApproovAuthentication(
                        request, approovToken, getCombinedBindingValue(request), true);
            default:
                return buildAuthenticationForDefaultPath(request, approovToken);
        }
    }

    private Authentication buildAuthenticationForDefaultPath(
            HttpServletRequest request, String approovToken) {
        boolean enforceBinding = ApiController.isTokenBindingEnabled();
        if (!enforceBinding) {
            return new ApproovAuthentication(request, approovToken, null, false);
        }

        return new ApproovAuthentication(
                request, approovToken, getSingleBindingValue(request), true);
    }

    private Authentication disabledAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "approov-disabled", null, Collections.emptyList());
    }

    private String getSingleBindingValue(HttpServletRequest request) {
        String headerName = approovConfig.getApproovTokenBindingHeaderName();
        if (!hasText(headerName)) {
            return null;
        }
        return trimOrNull(request.getHeader(headerName));
    }

    private String getCombinedBindingValue(HttpServletRequest request) {
        String authorization = trimOrNull(request.getHeader("Authorization"));
        String digest = trimOrNull(request.getHeader("Content-Digest"));
        if (!hasText(authorization) || !hasText(digest)) {
            return null;
        }
        return authorization + digest;
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
