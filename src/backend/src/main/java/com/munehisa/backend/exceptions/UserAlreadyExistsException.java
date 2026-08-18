package com.munehisa.backend.exceptions;

public class UserAlreadyExistsException extends LocalizedRuntimeException {
    public UserAlreadyExistsException() {
        super("error.userAlreadyExists");
    }
}
