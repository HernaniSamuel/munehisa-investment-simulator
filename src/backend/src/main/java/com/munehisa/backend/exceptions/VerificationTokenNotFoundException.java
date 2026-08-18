package com.munehisa.backend.exceptions;

public class VerificationTokenNotFoundException extends LocalizedRuntimeException {
    public VerificationTokenNotFoundException() {
        super("error.verificationTokenNotFound");
    }
}
