package com.munehisa.backend.infra.security;

import com.munehisa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the exact set of routes marked permitAll() in
 * SecurityConfig. Builds the real security filter chain in a minimal Spring
 * context (no Testcontainers, no DB, no application controllers) so that
 * adding or removing a permitAll() route flips a request from 404 (security
 * let it through, no handler registered) to 401 (security blocked it) or
 * vice versa, without needing a full @SpringBootTest.
 */
class SecurityConfigTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityConfig.class, TestBeans.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Configuration
    static class TestBeans {
        @Bean
        SecurityFilter securityFilter() {
            TokenService tokenService = mock(TokenService.class);
            when(tokenService.validateToken(any())).thenReturn(Optional.empty());
            return new SecurityFilter(tokenService, mock(UserRepository.class));
        }

        @Bean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
            return new RestAuthenticationEntryPoint(JsonMapper.builder().build());
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost"));
            config.setAllowedMethods(List.of("GET", "POST"));

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            return source;
        }
    }

    @Test
    void permitAllRoutes_areReachableWithoutAuthentication() throws Exception {
        // No controllers are registered in this context, so a permit-all route
        // that clears security falls through to "no handler found" (404), while
        // one still guarded by security is stopped earlier with 401.
        mockMvc.perform(post("/auth/login")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/register")).andExpect(status().isNotFound());
        mockMvc.perform(get("/auth/verify")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/resend-verification")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/forgot-password")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/reset-password")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs/foo")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }

    @Test
    void everyOtherRoute_requiresAuthentication() throws Exception {
        // Same paths as above but with the "wrong" HTTP method, so they fall
        // outside the permit-all matchers and land on anyRequest().authenticated().
        mockMvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/register")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/verify")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/resend-verification")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/forgot-password")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/reset-password")).andExpect(status().isUnauthorized());

        // Real endpoints that are intentionally not in the permit-all list.
        mockMvc.perform(get("/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/delete")).andExpect(status().isUnauthorized());
    }
}
