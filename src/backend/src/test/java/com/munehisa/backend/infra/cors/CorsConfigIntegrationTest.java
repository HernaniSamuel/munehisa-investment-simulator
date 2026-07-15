package com.munehisa.backend.infra.cors;

import com.munehisa.backend.controllers.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the "OPTION" (missing trailing S) typo that used to be
 * in CorsConfig's allowed-methods list. Spring validates a preflight against
 * the real verb in Access-Control-Request-Method (e.g. PATCH), never against
 * the literal string "OPTIONS", so the typo never actually blocked real
 * preflights - it only meant the Access-Control-Allow-Methods response header
 * echoed "OPTION" instead of "OPTIONS", which this test guards against.
 */
@Tag("integration")
class CorsConfigIntegrationTest extends IntegrationTestBase {

    @Test
    void preflightForPatch_echoesOptionsCorrectlyInAllowedMethods() throws Exception {
        MvcResult result = mockMvc.perform(options("/user")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andReturn();

        String allowedMethods = result.getResponse().getHeader("Access-Control-Allow-Methods");
        assertNotNull(allowedMethods);
        assertTrue(allowedMethods.contains("PATCH"));
        assertTrue(allowedMethods.contains("OPTIONS"));
    }
}
