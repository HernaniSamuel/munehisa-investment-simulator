package com.munehisa.backend.exceptions;

public class ResetPasswordTokenExpiredException extends LocalizedRuntimeException {
    public ResetPasswordTokenExpiredException() {
        super("error.resetPasswordTokenExpired");
    }
}
