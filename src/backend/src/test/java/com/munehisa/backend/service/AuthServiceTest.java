package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.*;
import com.munehisa.backend.exceptions.*;
import com.munehisa.backend.infra.security.TokenService;
import com.munehisa.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests: no Spring context is started here (no @SpringBootTest).
 * MockitoExtension only wires the {@code @Mock}/{@code @InjectMocks} fields below
 * and nothing else, which is what keeps these tests fast (milliseconds, no I/O).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private AccountLockoutService accountLockoutService;

    @InjectMocks
    private AuthService authService;

    private static final long VERIFICATION_TOKEN_EXPIRATION_MS = 1_800_000L; // 30 min
    private static final long RESET_TOKEN_EXPIRATION_MS = 900_000L; // 15 min

    @BeforeEach
    void setUp() {
        // AuthService's expiration fields are populated by @Value in production
        // (Spring reads them from application.properties). @InjectMocks only
        // knows how to fill the constructor-injected fields (repository,
        // passwordEncoder, tokenService, emailService) coming from Lombok's
        // @RequiredArgsConstructor - the two @Value longs are left at their
        // default of 0 unless we set them ourselves via reflection.
        ReflectionTestUtils.setField(authService, "verificationTokenExpirationMs", VERIFICATION_TOKEN_EXPIRATION_MS);
        ReflectionTestUtils.setField(authService, "resetPasswordTokenExpirationMs", RESET_TOKEN_EXPIRATION_MS);
    }

    private User buildUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Ada Lovelace");
        user.setEmail("ada@example.com");
        user.setPassword("hashed-password");
        user.setVerified(true);
        return user;
    }

    // ---------- register ----------

    @Test
    void register_success_savesUnverifiedUserAndSendsVerificationEmail() {
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "plain-password");
        when(repository.findByEmail(body.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(body.password())).thenReturn("hashed-password");

        authService.register(body);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(repository).save(savedUser.capture());
        User user = savedUser.getValue();
        assertEquals("Ada Lovelace", user.getName());
        assertEquals("ada@example.com", user.getEmail());
        assertEquals("hashed-password", user.getPassword());
        assertFalse(user.isVerified());
        assertNotNull(user.getVerificationToken());
        assertTrue(user.getVerificationTokenExpiry().isAfter(Instant.now()));

        verify(emailService).sendVerificationEmail(eq("ada@example.com"), eq(user.getVerificationToken()));
    }

    @Test
    void register_emailAlreadyVerified_throwsUserAlreadyExists() {
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "plain-password");
        User existing = buildUser();
        existing.setVerified(true);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(existing));

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(body));
        verify(repository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void register_emailPendingVerification_throwsEmailPendingVerification() {
        RegisterRequestDTO body = new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "plain-password");
        User existing = buildUser();
        existing.setVerified(false);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(existing));

        assertThrows(EmailPendingVerificationException.class, () -> authService.register(body));
        verify(repository, never()).save(any());
    }

    // ---------- login ----------

    @Test
    void login_success_returnsJwt() {
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "plain-password");
        User user = buildUser();
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));
        when(accountLockoutService.checkPassword(user, body.password())).thenReturn(true);
        when(tokenService.generateToken(user)).thenReturn("jwt-token");

        LoginResponseDTO response = authService.login(body);

        assertEquals("Ada Lovelace", response.name());
        assertEquals("jwt-token", response.token());
    }

    @Test
    void login_userNotFound_throwsInvalidCredentials() {
        LoginRequestDTO body = new LoginRequestDTO("unknown@example.com", "plain-password");
        when(repository.findByEmail(body.email())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(body));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "wrong-password");
        User user = buildUser();
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));
        when(accountLockoutService.checkPassword(user, body.password())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(body));
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    void login_unverifiedEmail_throwsEmailPendingVerification() {
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "plain-password");
        User user = buildUser();
        user.setVerified(false);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));
        when(accountLockoutService.checkPassword(user, body.password())).thenReturn(true);

        assertThrows(EmailPendingVerificationException.class, () -> authService.login(body));
    }

    // ---------- verify (email verification) ----------

    @Test
    void verify_success_marksUserVerifiedAndReturnsJwt() {
        User user = buildUser();
        user.setVerified(false);
        user.setVerificationToken("valid-token");
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(60));
        when(repository.findByVerificationToken("valid-token")).thenReturn(Optional.of(user));
        when(tokenService.generateToken(user)).thenReturn("jwt-token");

        LoginResponseDTO response = authService.verify("valid-token");

        assertEquals("jwt-token", response.token());
        assertTrue(user.isVerified());
        assertNull(user.getVerificationToken());
        assertNull(user.getVerificationTokenExpiry());
        verify(repository).save(user);
    }

    @Test
    void verify_expiredToken_throwsVerificationTokenExpired() {
        User user = buildUser();
        user.setVerificationToken("expired-token");
        user.setVerificationTokenExpiry(Instant.now().minusSeconds(60));
        when(repository.findByVerificationToken("expired-token")).thenReturn(Optional.of(user));

        assertThrows(VerificationTokenExpiredException.class, () -> authService.verify("expired-token"));
        verify(repository, never()).save(any());
    }

    @Test
    void verify_alreadyUsedToken_throwsVerificationTokenNotFound() {
        // verify() nulls the token out on success, so replaying the same token
        // string afterwards means findByVerificationToken() finds nothing -
        // "already used" and "never existed" are the same code path here.
        when(repository.findByVerificationToken("used-token")).thenReturn(Optional.empty());

        assertThrows(VerificationTokenNotFoundException.class, () -> authService.verify("used-token"));
    }

    // ---------- resendEmail ----------

    @Test
    void resendEmail_success_issuesNewTokenAndReturnsEmpty() {
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");
        User user = buildUser();
        user.setVerified(false);
        user.setVerificationToken("old-token");
        user.setVerificationTokenExpiry(Instant.now().minusSeconds(60)); // expired -> allowed to resend
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));

        Optional<ResendEmailResponseDTO> result = authService.resendEmail(body);

        assertTrue(result.isEmpty());
        assertNotEquals("old-token", user.getVerificationToken());
        assertTrue(user.getVerificationTokenExpiry().isAfter(Instant.now()));
        verify(repository).save(user);
        verify(emailService).sendVerificationEmail(eq("ada@example.com"), eq(user.getVerificationToken()));
    }

    @Test
    void resendEmail_blockedWhileValidTokenExists_returnsMessageWithoutSendingNewToken() {
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");
        User user = buildUser();
        user.setVerified(false);
        user.setVerificationToken("still-valid-token");
        Instant expiry = Instant.now().plusSeconds(60);
        user.setVerificationTokenExpiry(expiry);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));

        Optional<ResendEmailResponseDTO> result = authService.resendEmail(body);

        assertTrue(result.isPresent());
        assertEquals(expiry, result.get().resendAvailableAt());
        assertEquals("still-valid-token", user.getVerificationToken());
        verify(repository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resendEmail_userNotFound_throwsInvalidCredentials() {
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("unknown@example.com");
        when(repository.findByEmail(body.email())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.resendEmail(body));
    }

    @Test
    void resendEmail_alreadyVerified_throwsUserAlreadyExists() {
        ResendEmailRequestDTO body = new ResendEmailRequestDTO("ada@example.com");
        User user = buildUser();
        user.setVerified(true);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));

        assertThrows(UserAlreadyExistsException.class, () -> authService.resendEmail(body));
    }

    // ---------- forgotPassword (password reset request) ----------

    @Test
    void forgotPassword_success_issuesResetTokenAndSendsEmail() {
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");
        User user = buildUser();
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));

        Optional<ForgotPasswordResponseDTO> result = authService.forgotPassword(body);

        assertTrue(result.isEmpty());
        assertNotNull(user.getResetPasswordToken());
        assertTrue(user.getResetPasswordTokenExpiry().isAfter(Instant.now()));
        verify(repository).save(user);
        verify(emailService).sendPasswordRecoverEmail(eq("ada@example.com"), eq(user.getResetPasswordToken()));
    }

    @Test
    void forgotPassword_blockedWhileValidTokenExists_doesNotIssueNewToken() {
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("ada@example.com");
        User user = buildUser();
        user.setResetPasswordToken("still-valid-token");
        Instant expiry = Instant.now().plusSeconds(60);
        user.setResetPasswordTokenExpiry(expiry);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));

        Optional<ForgotPasswordResponseDTO> result = authService.forgotPassword(body);

        assertTrue(result.isPresent());
        assertEquals(expiry, result.get().resendAvailableAt());
        assertEquals("still-valid-token", user.getResetPasswordToken());
        verify(repository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_userNotFound_throwsInvalidCredentials() {
        ForgotPasswordRequestDTO body = new ForgotPasswordRequestDTO("unknown@example.com");
        when(repository.findByEmail(body.email())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.forgotPassword(body));
    }

    // ---------- resetPassword (password reset confirm) ----------

    @Test
    void resetPassword_success_updatesPasswordAndReturnsJwt() {
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("valid-reset-token", "new-plain-password");
        User user = buildUser();
        user.setResetPasswordToken("valid-reset-token");
        user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(60));
        when(repository.findByResetPasswordToken("valid-reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-plain-password")).thenReturn("new-hashed-password");
        when(tokenService.generateToken(user)).thenReturn("jwt-token");

        LoginResponseDTO response = authService.resetPassword(body);

        assertEquals("jwt-token", response.token());
        assertEquals("new-hashed-password", user.getPassword());
        assertNull(user.getResetPasswordToken());
        assertNull(user.getResetPasswordTokenExpiry());
        verify(repository).save(user);
    }

    @Test
    void resetPassword_expiredToken_throwsResetPasswordTokenExpired() {
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("expired-reset-token", "new-plain-password");
        User user = buildUser();
        user.setResetPasswordToken("expired-reset-token");
        user.setResetPasswordTokenExpiry(Instant.now().minusSeconds(60));
        when(repository.findByResetPasswordToken("expired-reset-token")).thenReturn(Optional.of(user));

        assertThrows(ResetPasswordTokenExpiredException.class, () -> authService.resetPassword(body));
        verify(repository, never()).save(any());
    }

    @Test
    void resetPassword_alreadyUsedToken_throwsResetPasswordTokenNotFound() {
        // Same reasoning as verify(): resetPassword() nulls the token on success,
        // so a replayed token is indistinguishable from one that never existed.
        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("used-reset-token", "new-plain-password");
        when(repository.findByResetPasswordToken("used-reset-token")).thenReturn(Optional.empty());

        assertThrows(ResetPasswordTokenNotFoundException.class, () -> authService.resetPassword(body));
    }

    @Test
    void login_accountLocked_propagatesException() {
        LoginRequestDTO body = new LoginRequestDTO("ada@example.com", "any-password");
        User user = buildUser();
        Instant lockedUntil = Instant.now().plusSeconds(60);
        when(repository.findByEmail(body.email())).thenReturn(Optional.of(user));
        when(accountLockoutService.checkPassword(user, body.password())).thenThrow(new AccountLockedException(lockedUntil));

        assertThrows(AccountLockedException.class, () -> authService.login(body));
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    void resetPassword_accountLocked_resetsFailedAttemptsAndUnlocksAccount() {
        User user = buildUser();
        user.setLockedUntil(Instant.now().plusMillis(900000));
        user.setFailedLoginAttempts(5);
        user.setResetPasswordToken("some-reset-token");
        user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(60));

        when(repository.findByResetPasswordToken("some-reset-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new password")).thenReturn("new-hashed-password");
        when(tokenService.generateToken(user)).thenReturn("jwt-token");

        ResetPasswordRequestDTO body = new ResetPasswordRequestDTO("some-reset-token", "new password");
        authService.resetPassword(body);

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }
}
