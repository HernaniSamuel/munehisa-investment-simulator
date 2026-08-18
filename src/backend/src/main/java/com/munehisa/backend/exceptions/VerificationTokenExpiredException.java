package com.munehisa.backend.exceptions;

public class VerificationTokenExpiredException extends LocalizedRuntimeException {
    public VerificationTokenExpiredException() {
        super("error.verificationTokenExpired");
    }
}
