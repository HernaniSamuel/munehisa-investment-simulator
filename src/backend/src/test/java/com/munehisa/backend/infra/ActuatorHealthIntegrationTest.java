package com.munehisa.backend.infra;

import com.munehisa.backend.controllers.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /actuator/health must stay reachable without authentication - it's what container
 * and orchestrator health checks call, and they never send a JWT. Runs against the real
 * Testcontainers Postgres from IntegrationTestBase, so a real DB connection is what makes
 * the aggregated status "UP" here.
 */
@Tag("integration")
class ActuatorHealthIntegrationTest extends IntegrationTestBase {

    @Test
    void health_returnsUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
