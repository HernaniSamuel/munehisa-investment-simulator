package com.munehisa.backend.infra;

import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the application-prod.properties behavior added by issue #134: the GitHub Pages
 * frontend is allowed through CORS, Swagger/OpenAPI stay unreachable without
 * SWAGGER_UI_ENABLED, and only /actuator/health is exposed. Runs the real "prod" profile
 * against the shared Testcontainers Postgres; the other secrets that application.properties
 * requires (JWT, mail, data-service) but the issue leaves out of scope are stubbed here just
 * so the context can start.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "FRONTEND_URL=https://hernanisamuel.github.io",
        "api.security.token.secret=test-secret-key-not-for-production-use-only-for-tests",
        "spring.mail.password=test-email-password",
        "data-service.api-key=test-data-service-api-key",
        // Pinned directly rather than left to SWAGGER_UI_ENABLED's default: a developer's
        // local .env (loaded by spring-dotenv, which outranks application-prod.properties)
        // may well set SWAGGER_UI_ENABLED=true for their own dev profile use, which would
        // otherwise leak into this "prod, unset" scenario and make it flaky depending on
        // who/where it runs.
        "springdoc.swagger-ui.enabled=false",
        "springdoc.api-docs.enabled=false"
})
@Tag("integration")
class ProdProfileIntegrationTest extends SharedPostgresContainer {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void corsAllowsConfiguredProductionOrigin() throws Exception {
        MvcResult result = mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://hernanisamuel.github.io")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("https://hernanisamuel.github.io",
                result.getResponse().getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void swaggerUiAndApiDocs_areNotReachable() throws Exception {
        // springdoc redirects the legacy /swagger-ui.html path even when disabled, so this
        // checks "not 200" (as the acceptance criterion states) rather than a specific code.
        assertNotEquals(200, mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus());
        assertNotEquals(200, mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus());
    }

    @Test
    void actuatorHealth_isReachable_envAndBeansAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        assertNotEquals(200, mockMvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus());
        assertNotEquals(200, mockMvc.perform(get("/actuator/beans")).andReturn().getResponse().getStatus());
    }
}
