package com.munehisa.backend.exceptions;

public class ExchangeRateDataServiceException extends RuntimeException {
    public ExchangeRateDataServiceException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super("Failed to fetch exchange rate series for " + baseCurrency + "/" + quoteCurrency + " from data-service", cause);
    }
}
