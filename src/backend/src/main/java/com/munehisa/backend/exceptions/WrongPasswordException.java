package com.munehisa.backend.exceptions;

public class WrongPasswordException extends RuntimeException{
    public WrongPasswordException() { super("Wrong password."); }
}
