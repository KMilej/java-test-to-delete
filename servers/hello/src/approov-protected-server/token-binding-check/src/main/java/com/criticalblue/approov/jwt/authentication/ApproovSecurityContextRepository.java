package com.criticalblue.approov.jwt.authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.criticalblue.approov.jwt.ApiController;
import com.criticalblue.approov.jwt.WebSecurityConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Sets up the Approov Authentication in the Spring SecurityContext.
 * Decides per-endpoint whether token binding is enforced and, for /token-binding-2,
 * composes a double-value binding (Authorization + Content-Digest).
 *
 * @see WebSecurityConfig
 */
public class ApproovSecurityContextRepository implements SecurityContextRepository {

    private final ApproovConfig approovConfig;

    public ApproovSecurityContextRepository(ApproovConfig approovConfig) {
        this.approovConfig = approovConfig;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        HttpServletRequest request = requestResponseHolder.getRequest();
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // 1) Read Approov-Token (name is configurable in ApproovConfig)
        String approovToken = request.getHeader(approovConfig.getApproovHeaderName());
        if (approovToken == null) {
            // No token -> leave context empty; protected endpoints will be rejected later.
            return context;
        }

        // 2) Decide per endpoint
        String path = request.getRequestURI();
        if (path == null) path = "";

        boolean enforceBinding;
        Authentication approovAuthentication;

        switch (path) {
            case "/":
            case "/token-check": {
                // No token binding for these
                enforceBinding = false;
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, null, false);
                break;
            }

            case "/token-binding-check": {
                // Single-value token binding (e.g., Authorization header)
                enforceBinding = true;
                String single = getSingleBindingValue(request);
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, single, true);
                break;
            }

            case "/token-binding-check-with-two-values": {
                // Double-value token binding: "Authorization==Content-Digest=="
                enforceBinding = true;
                String combined = getCombinedBindingValue(request);
                approovAuthentication = new ApproovAuthentication(
                        approovConfig, approovToken, combined, true);
                break;
            }

            default: {
                // Fallback to global toggle
                enforceBinding = ApiController.isTokenBindingEnebled;
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

        // 3) Put into context
        context.setAuthentication(approovAuthentication);
        return context;
    }

    /**
     * Single-value binding: read the header configured in ApproovConfig (e.g., "Authorization").
     */
    private String getSingleBindingValue(HttpServletRequest request) {
        final String headerName = approovConfig.getApproovTokenBindingHeaderName(); // e.g. "Authorization"
        if (headerName == null) return null;
        final String value = request.getHeader(headerName);
        return value == null ? null : value.trim();
    }

    /**
     * Double-value binding for /token-binding-2:
     * Must MATCH EXACTLY the CLI input used in:
     *   approov token -setDataHashInToken "AuthorizationValue==ContentDigestValue==" -genExample <aud>
     */
    private String getCombinedBindingValue(HttpServletRequest request) {
        String auth = trimOrNull(request.getHeader("Authorization"));
        String digest = trimOrNull(request.getHeader("Content-Digest"));
        if (auth == null || digest == null) return null;

        // !!! Keep format EXACTLY the same as in CLI !!!
        // Example: "ExampleAuthToken==ContentDigest=="
        return auth + "==" + digest + "==";
    }

    private String trimOrNull(String v) {
        return v == null ? null : v.trim();
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        // Stateless: nothing to save
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        // Safer for concurrency: read directly from request
        return request.getHeader(approovConfig.getApproovHeaderName()) != null;
    }
}
