package com.criticalblue.approov.jwt;

import com.criticalblue.approov.jwt.authentication.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private static ApproovConfig approovConfig = ApproovConfig.getInstance();

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(Arrays.asList("GET"));
        configuration.addAllowedHeader("Authorization");
        configuration.addAllowedHeader("Approov-Token");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/error");
    }

    @Configuration
    // @IMPORTANT Approov token check must be at Order 1. Any other type of
    //            Authentication (User, API Key, etc.) for the request should go
    //            after this one with @Order(2).
    @Order(1)
    public static class ApproovWebSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http.cors();

            http
// *** COMMENT THE LINE BELOW FOR APPROOV ***
             //.authorizeRequests().antMatchers("/**").permitAll().and()
                    .httpBasic().disable()
                    .formLogin().disable()
                    .logout().disable()
                    .csrf().disable()

// *** UNCOMMENT THE LINE BELOW FOR APPROOV USING SECRETS PROTECTION ***

                    // @APPROOV The Approov Token check is triggered here.
                    .authenticationProvider(new ApproovAuthenticationProvider(approovConfig))
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

            http
                    .securityContext()
                    // @APPROOV The Approov Token check is configured here.
                    .securityContextRepository(new ApproovSecurityContextRepository(approovConfig))
                    .and()
                    .exceptionHandling()
                    .authenticationEntryPoint(new ApproovAuthenticationEntryPoint())
                    .and()
                    // @APPROOV This matcher will require the Approov token for all API endpoints.
//                     .antMatcher("/")
//                         .authorizeRequests()
//                         .antMatchers(HttpMethod.GET, "/**").authenticated();authenticated
                    .authorizeRequests()
                    // public root
                    .antMatchers("/").permitAll()
                    // require Approov token (and binding/signing enforced by your Approov components/config)
                    .antMatchers("/token-check").authenticated()
                    .antMatchers("/token-binding-check").authenticated()
                    .antMatchers("/message-signing-check").authenticated()
                    .antMatchers("/token-binding-check-with-two-values").authenticated()
                    // anything else is denied (optional but good practice)
                    .anyRequest().denyAll();
        }
    }
}
