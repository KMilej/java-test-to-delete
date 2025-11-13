package com.criticalblue.approov.jwt;

import java.util.Collections;

import com.criticalblue.approov.jwt.authentication.ApproovAuthenticationEntryPoint;
import com.criticalblue.approov.jwt.authentication.ApproovAuthenticationProvider;
import com.criticalblue.approov.jwt.authentication.ApproovConfig;
import com.criticalblue.approov.jwt.authentication.ApproovSecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    private static final ApproovConfig APPROOV_CONFIG = ApproovConfig.getInstance();

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedMethods(Collections.singletonList("GET"));
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
    @Order(1)
    public static class ApproovWebSecurityConfig extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.cors()
                    .and()
                    .httpBasic().disable()
                    .formLogin().disable()
                    .logout().disable()
                    .csrf().disable()
                    .authenticationProvider(new ApproovAuthenticationProvider(APPROOV_CONFIG))
                    .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    .securityContext()
                    .securityContextRepository(
                            new ApproovSecurityContextRepository(APPROOV_CONFIG))
                    .and()
                    .exceptionHandling()
                    .authenticationEntryPoint(new ApproovAuthenticationEntryPoint())
                    .and()
                    .authorizeRequests()
                    .antMatchers("/").permitAll()
                    .antMatchers("/unprotected").permitAll()
                    .antMatchers(
                            "/approov-state",
                            "/approov/enable",
                            "/approov/disable",
                            "/approov/toggle")
                    .permitAll()
                    .antMatchers("/sfv_test").permitAll()
                    .antMatchers("/token-check").authenticated()
                    .antMatchers("/token-binding-1").authenticated()
                    .antMatchers("/token-binding-2").authenticated()
                    .antMatchers("/message-signing-check").authenticated();
        }
    }
}
