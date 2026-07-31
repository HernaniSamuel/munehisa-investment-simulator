package com.munehisa.backend.infra;

import com.munehisa.backend.dto.AccountLockedResponseDTO;
import com.munehisa.backend.exceptions.*;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;


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
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> verificationTokenExpiredHandler(VerificationTokenExpiredException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResetPasswordTokenNotFoundException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenNotFoundHandler(ResetPasswordTokenNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResetPasswordTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenExpiredHandler(ResetPasswordTokenExpiredException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<RestErrorMessage> emailSendHandler(EmailSendException exception) {
        return buildErrorResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<AccountLockedResponseDTO> accountLockedHandler(AccountLockedException exception) {
        AccountLockedResponseDTO body = new AccountLockedResponseDTO(
                exception.getMessage(),
                exception.getLockedUntil()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(SimulationNotFoundException.class)
    public ResponseEntity<RestErrorMessage> simulationNotFoundHandler(SimulationNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FutureSimulationStartMonthException.class)
    public ResponseEntity<RestErrorMessage> futureSimulationStartMonthHandler(FutureSimulationStartMonthException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SnapshotNotFoundException.class)
    public ResponseEntity<RestErrorMessage> snapshotNotFoundHandler(SnapshotNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientCashBalanceException.class)
    public ResponseEntity<RestErrorMessage> insufficientCashBalanceHandler(InsufficientCashBalanceException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FutureDeflationTargetException.class)
    public ResponseEntity<RestErrorMessage> futureDeflationTargetHandler(FutureDeflationTargetException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnsupportedDeflationCurrencyException.class)
    public ResponseEntity<RestErrorMessage> unsupportedDeflationCurrencyHandler(UnsupportedDeflationCurrencyException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<RestErrorMessage> assetNotFoundHandler(AssetNotFoundException exception) {
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AssetPredatesStartDateException.class)
    public ResponseEntity<RestErrorMessage> assetPredatesStartDateHandler(AssetPredatesStartDateException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientCashForPurchaseException.class)
    public ResponseEntity<RestErrorMessage> insufficientCashForPurchaseHandler(InsufficientCashForPurchaseException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientPositionQuantityException.class)
    public ResponseEntity<RestErrorMessage> insufficientPositionQuantityHandler(InsufficientPositionQuantityException exception) {
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        RestErrorMessage errorMessage = new RestErrorMessage(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }
}
