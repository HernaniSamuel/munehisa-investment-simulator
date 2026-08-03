package com.munehisa.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.munehisa.backend.exceptions.AccountLockedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UnknownEmailThrottleServiceTest {

    private static final long MAX_ATTEMPTS = 3L;
    private static final long WINDOW_MS = 900_000L;
    private static final long MAX_TRACKED_EMAILS = 3L;

    private UnknownEmailThrottleService service;

    @BeforeEach
    void setUp() {
        service = new UnknownEmailThrottleService();
        ReflectionTestUtils.setField(service, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(service, "windowMs", WINDOW_MS);
        ReflectionTestUtils.setField(service, "maxTrackedEmails", MAX_TRACKED_EMAILS);
        ReflectionTestUtils.invokeMethod(service, "init");
    }

    @Test
    void throttle_belowThreshold_doesNotThrow() {
        assertDoesNotThrow(() -> service.throttle("nobody@example.com"));
        assertDoesNotThrow(() -> service.throttle("nobody@example.com"));
    }

    @Test
    void throttle_reachesThreshold_throwsAccountLockedExceptionWithFutureLockedUntil() {
        service.throttle("nobody@example.com");
        service.throttle("nobody@example.com");

        AccountLockedException exception = assertThrows(AccountLockedException.class,
                () -> service.throttle("nobody@example.com"));

        assertTrue(exception.getLockedUntil().isAfter(Instant.now()));
    }

    @Test
    void throttle_alreadyLocked_throwsAgainWithoutExtendingLock() {
        service.throttle("nobody@example.com");
        service.throttle("nobody@example.com");
        AccountLockedException first = assertThrows(AccountLockedException.class,
                () -> service.throttle("nobody@example.com"));

        AccountLockedException second = assertThrows(AccountLockedException.class,
                () -> service.throttle("nobody@example.com"));

        assertEquals(first.getLockedUntil(), second.getLockedUntil());
    }

    @Test
    void throttle_distinctEmails_trackedIndependently() {
        service.throttle("a@example.com");
        service.throttle("a@example.com");

        assertDoesNotThrow(() -> service.throttle("b@example.com"));
    }

    @Test
    void throttle_exceedsMaxTrackedEmails_evictsBoundedly() {
        for (int i = 0; i < MAX_TRACKED_EMAILS + 2; i++) {
            service.throttle("attacker-" + i + "@example.com");
        }

        @SuppressWarnings("unchecked")
        Cache<String, Object> attempts = (Cache<String, Object>) ReflectionTestUtils.getField(service, "attempts");
        attempts.cleanUp();

        assertTrue(attempts.estimatedSize() <= MAX_TRACKED_EMAILS);
    }

    @Test
    void throttle_entryExpiresAfterWindow_resetsAsFreshAttempt() throws InterruptedException {
        ReflectionTestUtils.setField(service, "windowMs", 200L);
        ReflectionTestUtils.invokeMethod(service, "init");

        service.throttle("nobody@example.com");
        service.throttle("nobody@example.com");
        Thread.sleep(300);

        // if the entry hadn't expired, this 3rd attempt would cross MAX_ATTEMPTS and throw
        assertDoesNotThrow(() -> service.throttle("nobody@example.com"));
    }
}
