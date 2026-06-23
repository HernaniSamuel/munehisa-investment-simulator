package com.munehisa.backend.infra;

import com.munehisa.backend.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    public ResponseEntity<RestErrorMessage> buildErrorResponse(RuntimeException exceptionClass, HttpStatus httpStatus) {
        RestErrorMessage standardErrorMessage = new RestErrorMessage(httpStatus, exceptionClass.getMessage());
        return ResponseEntity.status(httpStatus).body(standardErrorMessage);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<RestErrorMessage> runtimeExceptionHandler(RuntimeException exception) {
        return buildErrorResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestErrorMessage> userAlreadyExistsHandler(UserAlreadyExistsException exception) {
        return buildErrorResponse(exception, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<RestErrorMessage> invalidCredentialsHandler(InvalidCredentialsException exception) {
        return buildErrorResponse(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailPendingVerificationException.class)
    public ResponseEntity<RestErrorMessage> emailPendingVerificationHandler(EmailPendingVerificationException exception) {
        return buildErrorResponse(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(VerificationTokenNotFoundException.class)
    public ResponseEntity<RestErrorMessage> verificationTokenNotFoundHandler(VerificationTokenNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> verificationTokenExpiredHandler(VerificationTokenExpiredException exception) {
        return buildErrorResponse(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResetPasswordTokenNotFoundException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenNotFoundHandler(ResetPasswordTokenNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResetPasswordTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenExpiredHandler(ResetPasswordTokenExpiredException exception) {
        return buildErrorResponse(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<RestErrorMessage> emailSendHandler(EmailSendException exception) {
        return buildErrorResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}