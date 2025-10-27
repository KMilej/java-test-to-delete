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

            http.cors().and()
                    .httpBasic().disable()
                    .formLogin().disable()
                    .logout().disable()
                    .csrf().disable()
                    .authenticationProvider(new ApproovAuthenticationProvider(approovConfig))
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .securityContext()
                    .securityContextRepository(new ApproovSecurityContextRepository(approovConfig))
                    .and()
                    .exceptionHandling()
                    .authenticationEntryPoint(new ApproovAuthenticationEntryPoint())
                    .and()
                    .authorizeRequests()
                    // public
                    .antMatchers("/unprotected").permitAll()
                    .antMatchers("/approov-state", "/approov/enable", "/approov/disable", "/approov/toggle").permitAll()
                    // secured (will still 200 when Approov is OFF due to dummy auth)
                    .antMatchers("/token-check").authenticated()
                    .antMatchers("/token-binding-1").authenticated()
                    .antMatchers("/token-binding-2").authenticated()
                    .antMatchers("/message-signing-check").authenticated()
            // everything else denied (optional but recommended)
            //.anyRequest().denyAll()
            ;
        }

    }
    }
