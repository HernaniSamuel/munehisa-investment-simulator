package com.munehisa.backend.exceptions;

public class EmailSendException extends LocalizedRuntimeException {
    public EmailSendException(Throwable cause) {
        super("error.emailSend", cause);
    }
}
