package com.munehisa.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.munehisa.backend.exceptions.AccountLockedException;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class UnknownEmailThrottleService {

    @Value("${login.unknown-email-throttle.max-attempts}")
    private long maxAttempts;

    @Value("${login.unknown-email-throttle.window}")
    private long windowMs;

    @Value("${login.unknown-email-throttle.max-tracked-emails}")
    private long maxTrackedEmails;

    private Cache<String, ThrottleEntry> attempts;

    @PostConstruct
    void init() {
        attempts = Caffeine.newBuilder()
                .expireAfterWrite(windowMs, TimeUnit.MILLISECONDS)
                .maximumSize(maxTrackedEmails)
                .build();
    }

    public void throttle(String email) {
        ThrottleEntry updated = attempts.asMap().compute(email, (key, existing) -> next(existing));

        if (updated.lockedUntil() != null) {
            throw new AccountLockedException(updated.lockedUntil());
        }
    }

    private ThrottleEntry next(ThrottleEntry existing) {
        Instant now = Instant.now();

        // still within a lock from a previous call: leave it untouched so repeated
        // probing doesn't keep pushing the lock further out
        if (existing != null && existing.lockedUntil() != null && existing.lockedUntil().isAfter(now)) {
            return existing;
        }

        long nextAttempts = (existing == null || existing.lockedUntil() != null) ? 1 : existing.attempts() + 1;

        if (nextAttempts >= maxAttempts) {
            return new ThrottleEntry(nextAttempts, now.plusMillis(windowMs));
        }

        return new ThrottleEntry(nextAttempts, null);
    }

    private record ThrottleEntry(long attempts, Instant lockedUntil) {}
}
