package com.munehisa.backend.exceptions;

public class VerificationTokenNotFoundException extends RuntimeException {
    public VerificationTokenNotFoundException() {
        super("Verification token not found.");
    }
}
