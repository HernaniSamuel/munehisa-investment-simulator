package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.infra.security.TokenService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class UserControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TokenService tokenService;

    @Test
    void updateName_validRequestWithToken_returns200() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        UpdateNameRequestDTO body = new UpdateNameRequestDTO("New Name");

        mockMvc.perform(patch("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        // .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateName_invalidRequestWithoutName_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        UpdateNameRequestDTO body = new UpdateNameRequestDTO("");

        mockMvc.perform(patch("/user")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateName_invalidRequestWithoutToken_returns401() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        UpdateNameRequestDTO body = new UpdateNameRequestDTO("");

        mockMvc.perform(patch("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
