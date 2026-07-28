package com.munehisa.backend.exceptions;

public class ExchangeRateUnavailableException extends RuntimeException {
    public ExchangeRateUnavailableException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super("No cached exchange rate data available for " + baseCurrency + "/" + quoteCurrency
                + " and refresh from data-service failed", cause);
    }
}
