package com.munehisa.backend.exceptions;

public class ExchangeRateDataServiceException extends LocalizedRuntimeException {
    public ExchangeRateDataServiceException(String baseCurrency, String quoteCurrency, Throwable cause) {
        super("error.exchangeRateDataService", cause, baseCurrency, quoteCurrency);
    }
}
