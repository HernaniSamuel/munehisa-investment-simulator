package com.munehisa.backend.infra;

import com.munehisa.backend.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private ResponseEntity<RestErrorMessage> defaultHandler(RuntimeException exceptionClass, HttpStatus httpStatus) {
        RestErrorMessage standardErrorMessage = new RestErrorMessage(httpStatus, exceptionClass.getMessage());
        return ResponseEntity.status(httpStatus).body(standardErrorMessage);
    }

    @ExceptionHandler(RuntimeException.class)
    private ResponseEntity<RestErrorMessage> runtimeExceptionHandler(RuntimeException exception) {
        return defaultHandler(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UserNotFoundException.class)
    private ResponseEntity<RestErrorMessage> userNotFoundHandler(UserNotFoundException exception) {
        return defaultHandler(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    private ResponseEntity<RestErrorMessage> userAlreadyExistsHandler(UserAlreadyExistsException exception) {
        return defaultHandler(exception, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(WrongPasswordException.class)
    private ResponseEntity<RestErrorMessage> wrongPasswordHandler(WrongPasswordException exception) {
        return defaultHandler(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailPendingVerificationException.class)
    private ResponseEntity<RestErrorMessage> emailPendingVerificationHandler(EmailPendingVerificationException exception) {
        return defaultHandler(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(VerificationTokenNotFoundException.class)
    private ResponseEntity<RestErrorMessage> verificationTokenNotFoundHandler(VerificationTokenNotFoundException exception) {
        return defaultHandler(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    private ResponseEntity<RestErrorMessage> verificationTokenExpiredHandler(VerificationTokenExpiredException exception) {
        return defaultHandler(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResetPasswordTokenNotFoundException.class)
    private ResponseEntity<RestErrorMessage> resetPasswordTokenNotFoundHandler(ResetPasswordTokenNotFoundException exception) {
        return defaultHandler(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResetPasswordTokenExpiredException.class)
    private ResponseEntity<RestErrorMessage> resetPasswordTokenExpiredHandler(ResetPasswordTokenNotFoundException exception) {
        return defaultHandler(exception, HttpStatus.BAD_REQUEST);
    }
}