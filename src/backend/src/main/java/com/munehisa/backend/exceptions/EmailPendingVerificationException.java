package com.munehisa.backend.exceptions;

public class EmailPendingVerificationException extends RuntimeException {
    public EmailPendingVerificationException() { super("The email should be verified."); }
}
