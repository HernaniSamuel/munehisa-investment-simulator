package com.munehisa.backend.exceptions;

public class ResetPasswordTokenExpiredException extends RuntimeException {
    public ResetPasswordTokenExpiredException() {
        super("Reset password token has expired.");
    }
}
