package com.munehisa.backend.exceptions;

public class ExchangeRateUnavailableException extends LocalizedRuntimeException {
    public ExchangeRateUnavailableException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super("error.exchangeRateUnavailable", cause, baseCurrency, quoteCurrency);
    }
}
