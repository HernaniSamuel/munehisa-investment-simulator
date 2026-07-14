package com.munehisa.backend.controllers;

import com.munehisa.backend.dto.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack test: real Spring context, real Spring Security filter chain,
 * real Postgres (via Testcontainers) with Flyway migrations actually applied
 * against it - the only thing replaced is EmailService, so no real SMTP call
 * is ever attempted.
 *
 * @Testcontainers + a static @Container field means one Postgres container is
 * started for this whole test class (not one per test method), matching the
 * "per test class" note in the issue.
 */

@Tag("integration")
class AuthControllerIntegrationTest extends IntegrationTestBase {

    // ---------- POST /auth/register ----------

    @Test
    void register_newEmail_returns201() throws Exception {
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "new-user@example.com", "some-password");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_duplicateVerifiedEmail_returns409() throws Exception {
        createUser(user -> user.setVerified(true));
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "some-password");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "not-an-email", "some-password");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /auth/login ----------

    @Test
    void login_correctCredentials_returns200WithJwt() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "correct-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        createUser(user -> user.setVerified(true));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unverifiedEmail_returns403() throws Exception {
        createUser(user -> user.setVerified(false));
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "correct-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /auth/verify ----------

    @Test
    void verify_validToken_returns200WithJwt() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("valid-token");
            user.setVerificationTokenExpiry(Instant.now().plusSeconds(60));
        });

        mockMvc.perform(get("/auth/verify").param("verificationToken", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void verify_expiredToken_returns400() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("expired-token");
            user.setVerificationTokenExpiry(Instant.now().minusSeconds(60));
        });

        mockMvc.perform(get("/auth/verify").param("verificationToken", "expired-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_unknownOrAlreadyUsedToken_returns400() throws Exception {
        mockMvc.perform(get("/auth/verify").param("verificationToken", "never-issued-token"))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /auth/resend-verification ----------

    @Test
    void resendEmail_expiredPreviousToken_returns204() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("old-token");
            user.setVerificationTokenExpiry(Instant.now().minusSeconds(60));
        });
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void resendEmail_stillValidToken_returns429() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("still-valid-token");
            user.setVerificationTokenExpiry(Instant.now().plusSeconds(60));
        });
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());
    }

    // ---------- POST /auth/forgot-password ----------

    @Test
    void forgotPassword_noPendingToken_returns204() throws Exception {
        createUser(user -> user.setVerified(true));
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPassword_stillValidToken_returns429() throws Exception {
        createUser(user -> {
            user.setVerified(true);
            user.setResetPasswordToken("still-valid-token");
            user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(60));
        });
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());
    }

    // ---------- POST /auth/reset-password ----------

    @Test
    void resetPassword_validToken_returns200WithJwt() throws Exception {
        createUser(user -> {
            user.setResetPasswordToken("valid-reset-token");
            user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(60));
        });
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("valid-reset-token", "brand-new-password");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        createUser(user -> {
            user.setResetPasswordToken("expired-reset-token");
            user.setResetPasswordTokenExpiry(Instant.now().minusSeconds(60));
        });
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("expired-reset-token", "brand-new-password");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_unknownOrAlreadyUsedToken_returns400() throws Exception {
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("never-issued-token", "brand-new-password");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_accountLocked_returns429() throws Exception {
        createUser(user -> {
            user.setName("Ada Lovelace");
            user.setEmail("adalovelace@test.com");
            user.setPassword("password");
            user.setVerified(true);
            user.setLockedUntil(Instant.now().plusSeconds(600));
        });
        LoginRequestDTO body = new LoginRequestDTO("adalovelace@test.com", "password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests()) // 429
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
    }
}
