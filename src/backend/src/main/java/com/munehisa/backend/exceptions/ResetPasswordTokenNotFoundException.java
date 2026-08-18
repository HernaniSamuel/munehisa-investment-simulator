package com.munehisa.backend.exceptions;

public class ResetPasswordTokenNotFoundException extends LocalizedRuntimeException {
    public ResetPasswordTokenNotFoundException() {
        super("error.resetPasswordTokenNotFound");
    }
}
