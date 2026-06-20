package com.munehisa.backend.exceptions;


public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException() { super("A verified user with this email already exists."); }
}
