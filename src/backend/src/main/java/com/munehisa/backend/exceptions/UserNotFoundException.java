package com.munehisa.backend.exceptions;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException() { super("User not found."); }
}
