package com.munehisa.backend.exceptions;

public class EmailSendException extends RuntimeException {
    public EmailSendException() {
        super("Failed to send email");
    }
}
