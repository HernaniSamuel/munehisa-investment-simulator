package com.munehisa.backend.exceptions;

public class InvalidCredentialsException extends LocalizedRuntimeException {
    public InvalidCredentialsException() {
        super("error.invalidCredentials");
    }
}
