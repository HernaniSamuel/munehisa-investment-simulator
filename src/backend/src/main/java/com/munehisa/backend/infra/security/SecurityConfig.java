package com.munehisa.backend.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    record PermitAllRoute(HttpMethod method, String pattern) {
    }

    /**
     * Single source of truth for the permit-all route list: used both to build
     * the filter chain below and by SecurityConfigTest, so the two can't drift
     * apart silently.
     */
    static final List<PermitAllRoute> PERMIT_ALL_ROUTES = List.of(
            new PermitAllRoute(HttpMethod.POST, "/auth/login"),
            new PermitAllRoute(HttpMethod.POST, "/auth/register"),
            new PermitAllRoute(HttpMethod.GET, "/auth/verify"),
            new PermitAllRoute(HttpMethod.POST, "/auth/resend-verification"),
            new PermitAllRoute(HttpMethod.POST, "/auth/forgot-password"),
            new PermitAllRoute(HttpMethod.POST, "/auth/reset-password")
    );

    static final List<String> PERMIT_ALL_PATTERNS = List.of(
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())

                .sessionManagement(session -> session.
                        sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(restAuthenticationEntryPoint))

                .authorizeHttpRequests(authorize -> {
                    for (PermitAllRoute route : PERMIT_ALL_ROUTES) {
                        authorize.requestMatchers(route.method(), route.pattern()).permitAll();
                    }
                    authorize.requestMatchers(PERMIT_ALL_PATTERNS.toArray(String[]::new)).permitAll();
                    authorize.anyRequest().authenticated();
                })
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
