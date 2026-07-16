package com.munehisa.backend.exceptions;

public class EmailSendException extends RuntimeException {
    public EmailSendException(Throwable cause) {
        super("Failed to send email", cause);
    }
}
