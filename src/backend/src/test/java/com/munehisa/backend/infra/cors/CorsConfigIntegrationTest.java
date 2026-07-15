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
 * in CorsConfig's allowed-methods list, which silently broke CORS preflight
 * for every method other than plain GET/POST.
 */
@Tag("integration")
class CorsConfigIntegrationTest extends IntegrationTestBase {

    @Test
    void preflightRequest_allowsOptionsMethod() throws Exception {
        MvcResult result = mockMvc.perform(options("/user")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "OPTIONS"))
                .andExpect(status().isOk())
                .andReturn();

        String allowedMethods = result.getResponse().getHeader("Access-Control-Allow-Methods");
        assertNotNull(allowedMethods);
        assertTrue(allowedMethods.contains("OPTIONS"));
    }
}
