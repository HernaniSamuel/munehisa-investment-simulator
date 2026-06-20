package com.munehisa.backend.exceptions;

import java.time.Instant;

public class VerificationTokenExpiredException extends RuntimeException {
    public VerificationTokenExpiredException() {
        super("Verification token has expired.");
    }
}
