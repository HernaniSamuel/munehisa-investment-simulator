package com.munehisa.backend.exceptions;

public class EmailPendingVerificationException extends LocalizedRuntimeException {
    public EmailPendingVerificationException() {
        super("error.emailPendingVerification");
    }
}
