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

import static com.criticalblue.approov.jwt.ApiController.isTokenBindingEnebled;

/**
 * Used to setup the Approov Authentication Context when configuring the Spring framework security.
 *
 * @see WebSecurityConfig
 */
public class ApproovSecurityContextRepository implements SecurityContextRepository {

    private String approovToken = null;

    final private ApproovConfig approovConfig;

    /**
     * Constructs with an instance of the Approov configuration, and with a boolean flag to indicate if is to check the
     * token binding in the Approov token.
     *
     * @param approovConfig     Extracted from the .env file in the root of the project.
     */
    public ApproovSecurityContextRepository(ApproovConfig approovConfig) {
        this.approovConfig = approovConfig;
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {

        String tokenBindingHeader = null;

        HttpServletRequest request = requestResponseHolder.getRequest();

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        approovToken = request.getHeader(approovConfig.getApproovHeaderName());

        if (approovToken == null) {
            // returning an empty security context in an endpoint protected by
            // Approov, will cause Spring to later throw this exception:
            //  org.springframework.security.access.AccessDeniedException: Access is denied
            return context;
        }


        // *** UNCOMMENT THE TWO LINES BELOW FOR APPROOV USING TOKEN BINDING ***

//         tokenBindingHeader = getTokenBindingHeader(request);
//         Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken, tokenBindingHeader);

        // *** COMMENT THE LINE BELOW FOR APPROOV TOKEN BINDING ***
//        Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken);




        if (isTokenBindingEnebled == true) {
            tokenBindingHeader = getTokenBindingHeader(request);
            Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken, tokenBindingHeader);
            context.setAuthentication(approovAuthentication);
            return context;
        } else {
            Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken);
            context.setAuthentication(approovAuthentication);
            return context;
        }



       // context.setAuthentication(approovAuthentication);

      //  return context;
    }

    private String getTokenBindingHeader(HttpServletRequest request) {

        final String headerName = approovConfig.getApproovTokenBindingHeaderName();

        if (headerName == null) {
            return null;
        }

        final String tokenBindingHeader = request.getHeader(headerName);

        if (tokenBindingHeader == null) {
            return null;
        }

        return tokenBindingHeader.trim();
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return approovToken != null;
    }
}
