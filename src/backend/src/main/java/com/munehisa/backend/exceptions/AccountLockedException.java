package com.munehisa.backend.exceptions;

import java.time.Instant;

public class AccountLockedException extends LocalizedRuntimeException {
    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("error.accountLocked");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
