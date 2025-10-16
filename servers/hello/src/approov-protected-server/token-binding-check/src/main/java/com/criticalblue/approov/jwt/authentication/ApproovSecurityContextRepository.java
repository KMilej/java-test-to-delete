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
        HttpServletRequest request = requestResponseHolder.getRequest();
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // 1) Pobierz Approov-Token
        approovToken = request.getHeader(approovConfig.getApproovHeaderName());
        if (approovToken == null) {
            // Brak tokenu -> później AccessDenied
            return context;
        }

        // 2) Decyzja per endpoint
        String path = request.getRequestURI();
        if (path == null) path = "";

        boolean enforceBinding;
        switch (path) {
            case "/":
            case "/token-check":
                enforceBinding = false; // te endpointy bez bindingu
                break;
            case "/token-binding-check":
            case "/token-binding-check-with-two-values":
                enforceBinding = true;  // te endpointy z bindingiem
                break;
            default:
                // fallback: możesz użyć globalnego toggla
                enforceBinding = ApiController.isTokenBindingEnebled;
                break;
        }

        // 3) Zbuduj Authentication z właściwym trybem
        Authentication approovAuthentication;
        if (enforceBinding) {
            String tokenBindingHeader = getTokenBindingHeader(request); // <<— TA METODA JEST NIŻEJ W TEJ KLASIE
            approovAuthentication = new ApproovAuthentication(
                    approovConfig, approovToken, tokenBindingHeader, true);
        } else {
            approovAuthentication = new ApproovAuthentication(
                    approovConfig, approovToken, null, false);
        }

        // 4) Do kontekstu
        context.setAuthentication(approovAuthentication);
        return context;
    }

    /** Ta metoda MUSI być w TEJ klasie (private ok) */
    private String getTokenBindingHeader(HttpServletRequest request) {
        final String headerName = approovConfig.getApproovTokenBindingHeaderName(); // np. "Authorization"
        if (headerName == null) return null;
        final String tokenBindingHeader = request.getHeader(headerName);
        return tokenBindingHeader == null ? null : tokenBindingHeader.trim();
    }


//    @Override
//    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
//
//        String tokenBindingHeader = null;
//
//        HttpServletRequest request = requestResponseHolder.getRequest();
//
//        SecurityContext context = SecurityContextHolder.createEmptyContext();
//
//        approovToken = request.getHeader(approovConfig.getApproovHeaderName());
//
//        if (approovToken == null) {
//            // returning an empty security context in an endpoint protected by
//            // Approov, will cause Spring to later throw this exception:
//            //  org.springframework.security.access.AccessDeniedException: Access is denied
//            return context;
//        }
//
//
//        // *** UNCOMMENT THE TWO LINES BELOW FOR APPROOV USING TOKEN BINDING ***
//
////         tokenBindingHeader = getTokenBindingHeader(request);
////         Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken, tokenBindingHeader);
//
//        // *** COMMENT THE LINE BELOW FOR APPROOV TOKEN BINDING ***
////        Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken);
//
//        Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken);
//        context.setAuthentication(approovAuthentication);
//        return context;
//
//
//        if (isTokenBindingEnebled == true) {
//            tokenBindingHeader = getTokenBindingHeader(request);
//            Authentication approovAuthentication = new ApproovAuthentication(approovConfig, approovToken, tokenBindingHeader);
//            context.setAuthentication(approovAuthentication);
//            return context;
//        }
//
//
//       // context.setAuthentication(approovAuthentication);
//
//      //  return context;
//    }

//    @Override
//    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
//        HttpServletRequest request = requestResponseHolder.getRequest();
//        SecurityContext context = SecurityContextHolder.createEmptyContext();
//
//        // 1) Pobierz Approov Token z nagłówka konfigurowalnego w ApproovConfig
//        approovToken = request.getHeader(approovConfig.getApproovHeaderName());
//        if (approovToken == null) {
//            // Brak tokenu -> Spring później podniesie AccessDeniedException na endpointach chronionych
//            return context;
//        }
//
//        // 2) Wybór ścieżki
//        String path = request.getRequestURI();
//        if (path == null) path = "";
//
//        // 3) Decyzja: z token bindingiem czy bez?
//        boolean useTokenBinding;
//        switch (path) {
//            case "/":
//            case "/token-check":
//                useTokenBinding = false; // Wariant BEZ token bindingu
//                break;
//            case "/token-binding-check":
//            case "/token-binding-check-with-two-values":
//                useTokenBinding = true;  // Wariant Z token bindingiem
//                break;
//            default:
//                // Fallback: respektuj globalny przełącznik (jeśli chcesz)
//                useTokenBinding = ApiController.isTokenBindingEnebled;
//                break;
//        }
//
//        // 4) Zbuduj odpowiedni Authentication
//        Authentication approovAuthentication;
//        if (useTokenBinding) {
//            String tokenBindingHeader = getTokenBindingHeader(request);
//            approovAuthentication = new ApproovAuthentication(approovConfig, approovToken, tokenBindingHeader);
//        } else {
//            approovAuthentication = new ApproovAuthentication(approovConfig, approovToken);
//        }
//
//        // 5) Wstaw do kontekstu
//        context.setAuthentication(approovAuthentication);
//        return context;
//    }
//
//
//    private String getTokenBindingHeader(HttpServletRequest request) {
//
//        final String headerName = approovConfig.getApproovTokenBindingHeaderName();
//
//        if (headerName == null) {
//            return null;
//        }
//
//        final String tokenBindingHeader = request.getHeader(headerName);
//
//        if (tokenBindingHeader == null) {
//            return null;
//        }
//
//        return tokenBindingHeader.trim();
//    }



    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return approovToken != null;
    }
}
