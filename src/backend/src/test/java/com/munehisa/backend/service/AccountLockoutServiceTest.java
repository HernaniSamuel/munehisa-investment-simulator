package com.munehisa.backend.service;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.LoginRequestDTO;
import com.munehisa.backend.exceptions.AccountLockedException;
import com.munehisa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountLockoutServiceTest {
    @InjectMocks
    private AccountLockoutService accountLockoutService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;

    private static final long MAX_ATTEMPTS = 5L;
    private static final long LOCK_TIME_MS = 900_000L; // 15 min

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountLockoutService, "loginMaxFailedAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(accountLockoutService, "accountLockTime", LOCK_TIME_MS);
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

    @Test
    void checkPassword_stillLocked_throwsWithoutCallingPasswordEncoder() {
        User user = buildUser();
        user.setLockedUntil(Instant.now().plusSeconds(60));

        assertThrows(AccountLockedException.class,
                () -> accountLockoutService.checkPassword(user, "any-password"));

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void checkPassword_correctPasswordNotLocked_returnsTrueAndResetsCounter() {
        User user = buildUser();
        user.setFailedLoginAttempts(3);

        LoginRequestDTO login = new LoginRequestDTO("ada@example.com", "hashed-password");

        when(passwordEncoder.matches(login.password(), user.getPassword())).thenReturn(true);
        accountLockoutService.checkPassword(user, login.password());

        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void checkPassword_wrongPasswordBelowThreshold_returnsFalseAndIncrementsCounter() {
        User user = buildUser();
        LoginRequestDTO login = new LoginRequestDTO("ada@example.com", "hashed-password");

        when(passwordEncoder.matches(login.password(), user.getPassword())).thenReturn(false);
        accountLockoutService.checkPassword(user, login.password());

        assertEquals(1, user.getFailedLoginAttempts());
    }

    @Test
    void checkPassword_wrongPasswordReachesThreshold_locksAccountAndThrows() {
        User user = buildUser();
        user.setFailedLoginAttempts(4);

        LoginRequestDTO login = new LoginRequestDTO("ada@example.com", "hashed-password");

        when(passwordEncoder.matches(login.password(), user.getPassword())).thenReturn(false);

        assertThrows(AccountLockedException.class, () -> accountLockoutService.checkPassword(user, login.password()));
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
    }

    @Test
    void checkPassword_lockExpiredAndPasswordCorrect_unlocksAndReturnsTrue() {
        User user = buildUser();
        user.setLockedUntil(Instant.now().minusSeconds(1));

        LoginRequestDTO login = new LoginRequestDTO("ada@example.com", "hashed-password");

        when(passwordEncoder.matches(login.password(), user.getPassword())).thenReturn(true);
        accountLockoutService.checkPassword(user, login.password());

        assertNull(user.getLockedUntil());
    }

    @Test
    void checkPassword_lockExpiredAndPasswordWrong_countsAsNewFailureReturnsFalse() {
        User user = buildUser();
        user.setLockedUntil(Instant.now().minusSeconds(1));

        LoginRequestDTO login = new LoginRequestDTO("ada@example.com", "hashed-password");

        when(passwordEncoder.matches(login.password(), user.getPassword())).thenReturn(false);
        accountLockoutService.checkPassword(user, login.password());

        assertNull(user.getLockedUntil());
        assertEquals(1, user.getFailedLoginAttempts());
    }
}
