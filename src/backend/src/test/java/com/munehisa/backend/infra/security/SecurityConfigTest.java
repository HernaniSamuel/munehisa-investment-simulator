package com.munehisa.backend.infra.security;

import com.munehisa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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

    /**
     * Hand-maintained mirror of SecurityConfig.PERMIT_ALL_ROUTES. Comparing it
     * against the production list (rather than only probing these paths with
     * MockMvc) is what makes an *added* permit-all route fail this test too,
     * not just a removed one - a route added to SecurityConfig without a
     * matching edit here breaks the equality assertion below.
     */
    private static final List<SecurityConfig.PermitAllRoute> EXPECTED_PERMIT_ALL_ROUTES = List.of(
            new SecurityConfig.PermitAllRoute(HttpMethod.POST, "/auth/login"),
            new SecurityConfig.PermitAllRoute(HttpMethod.POST, "/auth/register"),
            new SecurityConfig.PermitAllRoute(HttpMethod.GET, "/auth/verify"),
            new SecurityConfig.PermitAllRoute(HttpMethod.POST, "/auth/resend-verification"),
            new SecurityConfig.PermitAllRoute(HttpMethod.POST, "/auth/forgot-password"),
            new SecurityConfig.PermitAllRoute(HttpMethod.POST, "/auth/reset-password")
    );

    private static final List<String> EXPECTED_PERMIT_ALL_PATTERNS = List.of(
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    );

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
    void permitAllRouteList_matchesExpectedList() {
        // Direct equality against SecurityConfig's own list, not just behavioral
        // probing: this is what catches a route being ADDED to the permit-all
        // list without a corresponding update here (behavioral probing below
        // only re-confirms paths we already know to look for).
        assertEquals(EXPECTED_PERMIT_ALL_ROUTES, SecurityConfig.PERMIT_ALL_ROUTES);
        assertEquals(EXPECTED_PERMIT_ALL_PATTERNS, SecurityConfig.PERMIT_ALL_PATTERNS);
    }

    @Test
    void permitAllRoutes_areReachableWithoutAuthentication() throws Exception {
        // No controllers are registered in this context, so a permit-all route
        // that clears security falls through to "no handler found" (404), while
        // one still guarded by security is stopped earlier with 401.
        for (SecurityConfig.PermitAllRoute route : SecurityConfig.PERMIT_ALL_ROUTES) {
            mockMvc.perform(request(route.method(), route.pattern())).andExpect(status().isNotFound());
        }
        for (String pattern : SecurityConfig.PERMIT_ALL_PATTERNS) {
            mockMvc.perform(get(concreteTestPath(pattern))).andExpect(status().isNotFound());
        }
    }

    @Test
    void everyOtherRoute_requiresAuthentication() throws Exception {
        // A method not covered by any permit-all rule on the same path must
        // still fall onto anyRequest().authenticated().
        for (SecurityConfig.PermitAllRoute route : SecurityConfig.PERMIT_ALL_ROUTES) {
            mockMvc.perform(request(HttpMethod.PUT, route.pattern())).andExpect(status().isUnauthorized());
        }

        // Real endpoints that are intentionally not in the permit-all list.
        mockMvc.perform(get("/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/user")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/delete")).andExpect(status().isUnauthorized());
    }

    private static String concreteTestPath(String pattern) {
        return pattern.endsWith("/**") ? pattern.substring(0, pattern.length() - 2) + "probe" : pattern;
    }
}
