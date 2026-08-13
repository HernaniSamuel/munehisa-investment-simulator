package com.munehisa.backend.infra;

import com.munehisa.backend.dto.AccountLockedResponseDTO;
import com.munehisa.backend.exceptions.*;
import lombok.extern.slf4j.Slf4j;
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


@Slf4j
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    public ResponseEntity<RestErrorMessage> buildErrorResponse(RuntimeException exceptionClass, HttpStatus httpStatus) {
        RestErrorMessage standardErrorMessage = new RestErrorMessage(httpStatus, exceptionClass.getMessage());
        return ResponseEntity.status(httpStatus).body(standardErrorMessage);
    }

    private void logWarn(RuntimeException exception) {
        log.warn("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
    }

    private void logError(String message, RuntimeException exception) {
        log.error(message, exception);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<RestErrorMessage> runtimeExceptionHandler(RuntimeException exception) {
        log.error("Unhandled runtime exception reached the generic exception handler", exception);
        RestErrorMessage errorMessage = new RestErrorMessage(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
    }

    @ExceptionHandler(AssetUnavailableException.class)
    public ResponseEntity<RestErrorMessage> assetUnavailableHandler(AssetUnavailableException exception) {
        logError("Asset data unavailable", exception);
        return buildErrorResponse(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(ExchangeRateUnavailableException.class)
    public ResponseEntity<RestErrorMessage> exchangeRateUnavailableHandler(ExchangeRateUnavailableException exception) {
        logError("Exchange rate data unavailable", exception);
        return buildErrorResponse(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(InflationUnavailableException.class)
    public ResponseEntity<RestErrorMessage> inflationUnavailableHandler(InflationUnavailableException exception) {
        logError("Inflation data unavailable", exception);
        return buildErrorResponse(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(TickerSearchUnavailableException.class)
    public ResponseEntity<RestErrorMessage> tickerSearchUnavailableHandler(TickerSearchUnavailableException exception) {
        logError("Ticker search unavailable", exception);
        return buildErrorResponse(exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestErrorMessage> userAlreadyExistsHandler(UserAlreadyExistsException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<RestErrorMessage> invalidCredentialsHandler(InvalidCredentialsException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailPendingVerificationException.class)
    public ResponseEntity<RestErrorMessage> emailPendingVerificationHandler(EmailPendingVerificationException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(VerificationTokenNotFoundException.class)
    public ResponseEntity<RestErrorMessage> verificationTokenNotFoundHandler(VerificationTokenNotFoundException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> verificationTokenExpiredHandler(VerificationTokenExpiredException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResetPasswordTokenNotFoundException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenNotFoundHandler(ResetPasswordTokenNotFoundException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResetPasswordTokenExpiredException.class)
    public ResponseEntity<RestErrorMessage> resetPasswordTokenExpiredHandler(ResetPasswordTokenExpiredException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<RestErrorMessage> emailSendHandler(EmailSendException exception) {
        logError("Failed to send email", exception);
        return buildErrorResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<AccountLockedResponseDTO> accountLockedHandler(AccountLockedException exception) {
        logWarn(exception);
        AccountLockedResponseDTO body = new AccountLockedResponseDTO(
                exception.getMessage(),
                exception.getLockedUntil()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(SimulationNotFoundException.class)
    public ResponseEntity<RestErrorMessage> simulationNotFoundHandler(SimulationNotFoundException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(FutureSimulationStartMonthException.class)
    public ResponseEntity<RestErrorMessage> futureSimulationStartMonthHandler(FutureSimulationStartMonthException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FutureSimulationCurrentMonthException.class)
    public ResponseEntity<RestErrorMessage> futureSimulationCurrentMonthHandler(FutureSimulationCurrentMonthException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SnapshotNotFoundException.class)
    public ResponseEntity<RestErrorMessage> snapshotNotFoundHandler(SnapshotNotFoundException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientCashBalanceException.class)
    public ResponseEntity<RestErrorMessage> insufficientCashBalanceHandler(InsufficientCashBalanceException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FutureDeflationTargetException.class)
    public ResponseEntity<RestErrorMessage> futureDeflationTargetHandler(FutureDeflationTargetException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnsupportedDeflationCurrencyException.class)
    public ResponseEntity<RestErrorMessage> unsupportedDeflationCurrencyHandler(UnsupportedDeflationCurrencyException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<RestErrorMessage> assetNotFoundHandler(AssetNotFoundException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AssetPredatesStartDateException.class)
    public ResponseEntity<RestErrorMessage> assetPredatesStartDateHandler(AssetPredatesStartDateException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientCashForPurchaseException.class)
    public ResponseEntity<RestErrorMessage> insufficientCashForPurchaseHandler(InsufficientCashForPurchaseException exception) {
        logWarn(exception);
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientPositionQuantityException.class)
    public ResponseEntity<RestErrorMessage> insufficientPositionQuantityHandler(InsufficientPositionQuantityException exception) {
        logWarn(exception);
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

        log.warn("Validation failed: {}", message);
        RestErrorMessage errorMessage = new RestErrorMessage(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
    }
}
