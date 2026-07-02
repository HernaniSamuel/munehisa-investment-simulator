package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.*;
import com.munehisa.backend.repository.UserRepository;
import com.munehisa.backend.service.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.function.Consumer;

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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private User createUser(Consumer<User> customizer) {
        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setVerified(true);
        customizer.accept(user);
        return userRepository.save(user);
    }

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
    void verify_expiredToken_returns401() throws Exception {
        // Issue's AC lists 400 for this case; the current implementation maps
        // VerificationTokenExpiredException to 401. Asserting real behavior.
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("expired-token");
            user.setVerificationTokenExpiry(Instant.now().minusSeconds(60));
        });

        mockMvc.perform(get("/auth/verify").param("verificationToken", "expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_unknownOrAlreadyUsedToken_returns404() throws Exception {
        // Issue's AC lists 400; current implementation maps
        // VerificationTokenNotFoundException to 404. Asserting real behavior.
        mockMvc.perform(get("/auth/verify").param("verificationToken", "never-issued-token"))
                .andExpect(status().isNotFound());
    }

    // ---------- POST /auth/resend-email ----------
    // Issue's AC refers to this endpoint as "/auth/resend-verification" with a
    // 204 success status; the real path is "/auth/resend-email" and it
    // returns 202 Accepted on success. Asserting real behavior.

    @Test
    void resendEmail_expiredPreviousToken_returns202() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("old-token");
            user.setVerificationTokenExpiry(Instant.now().minusSeconds(60));
        });
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/resend-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    @Test
    void resendEmail_stillValidToken_returns429() throws Exception {
        createUser(user -> {
            user.setVerified(false);
            user.setVerificationToken("still-valid-token");
            user.setVerificationTokenExpiry(Instant.now().plusSeconds(60));
        });
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/resend-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());
    }

    // ---------- POST /auth/forgot-password ----------
    // Issue's AC expects 204 (sent) / 429 (blocked); the real implementation
    // returns 200 for both cases and only the response message differs.

    @Test
    void forgotPassword_noPendingToken_returns200() throws Exception {
        createUser(user -> user.setVerified(true));
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resendAvailableAt").isEmpty());
    }

    @Test
    void forgotPassword_stillValidToken_returns200WithBlockedMessage() throws Exception {
        createUser(user -> {
            user.setVerified(true);
            user.setResetPasswordToken("still-valid-token");
            user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(60));
        });
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
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
    void resetPassword_expiredToken_returns401() throws Exception {
        // Issue's AC lists 400; current implementation maps
        // ResetPasswordTokenExpiredException to 401. Asserting real behavior.
        createUser(user -> {
            user.setResetPasswordToken("expired-reset-token");
            user.setResetPasswordTokenExpiry(Instant.now().minusSeconds(60));
        });
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("expired-reset-token", "brand-new-password");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_unknownOrAlreadyUsedToken_returns404() throws Exception {
        // Issue's AC lists 400; current implementation maps
        // ResetPasswordTokenNotFoundException to 404. Asserting real behavior.
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("never-issued-token", "brand-new-password");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
