package com.munehisa.backend.exceptions;

public class ResetPasswordTokenNotFoundException extends RuntimeException {
    public ResetPasswordTokenNotFoundException() {
        super("Reset password token not found.");
    }
}
