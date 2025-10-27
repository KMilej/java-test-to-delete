package com.criticalblue.approov.jwt.authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.criticalblue.approov.jwt.ApiController;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // <-- ADD
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Collections; // <-- ADD

public class ApproovSecurityContextRepository implements SecurityContextRepository {

    private final ApproovConfig approovConfig;

    public ApproovSecurityContextRepository(ApproovConfig approovConfig) {
        this.approovConfig = approovConfig;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        HttpServletRequest request = requestResponseHolder.getRequest();
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // ─────────────────────────────────────────────────────────────────────
        // NEW: When Approov is disabled, auto-authenticate everything so
        // secured endpoints still return 200 and controller can show "skipped".
        // ─────────────────────────────────────────────────────────────────────
        if (!ApiController.isApproovEnabled()) {
            var dummy = new UsernamePasswordAuthenticationToken("approov-disabled", null, Collections.emptyList());
            context.setAuthentication(dummy);
            return context;
        }


        // Approov enabled → proceed with normal flow
        String approovToken = request.getHeader(approovConfig.getApproovHeaderName());
        if (approovToken == null) {
            // No token -> leave context empty; protected endpoints will be rejected later.
            return context;
        }

        String path = request.getRequestURI();
        if (path == null) path = "";

        Authentication approovAuthentication;
        switch (path) {
            case "/unprotected":
            case "/token-check": {
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, null, false);
                break;
            }
            case "/token-binding-1": {
                String single = getSingleBindingValue(request);
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, single, true);
                break;
            }
            case "/token-binding-2": {
                String combined = getCombinedBindingValue(request);
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, combined, true);
                break;
            }
            default: {
                // FIX: use the proper accessor, not the misspelled field
                boolean enforceBinding = ApiController.isTokenBindingEnabled();
                if (enforceBinding) {
                    String single = getSingleBindingValue(request);
                    approovAuthentication = new ApproovAuthentication(
                            approovConfig, approovToken, single, true);
                } else {
                    approovAuthentication = new ApproovAuthentication(
                            approovConfig, approovToken, null, false);
                }
                break;
            }
        }

        context.setAuthentication(approovAuthentication);
        return context;
    }

    private String getSingleBindingValue(HttpServletRequest request) {
        final String headerName = approovConfig.getApproovTokenBindingHeaderName(); // e.g. "Authorization"
        if (headerName == null) return null;
        final String value = request.getHeader(headerName);
        return value == null ? null : value.trim();
    }

    private String getCombinedBindingValue(HttpServletRequest request) {
        String auth = trimOrNull(request.getHeader("Authorization"));
        String digest = trimOrNull(request.getHeader("Content-Digest"));
        if (auth == null || digest == null) return null;
        return auth + digest; // must match bytes used with -setDataHashInToken
    }

    private String trimOrNull(String v) {
        return v == null ? null : v.trim();
    }

    @Override public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {}
    @Override
    public boolean containsContext(HttpServletRequest request) {
        return !ApiController.isApproovEnabled()
                || request.getHeader(approovConfig.getApproovHeaderName()) != null;
    }

}
