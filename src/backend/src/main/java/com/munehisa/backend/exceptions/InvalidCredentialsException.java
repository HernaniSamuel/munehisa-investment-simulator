package com.munehisa.backend.exceptions;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid Credentials");
    }
}
