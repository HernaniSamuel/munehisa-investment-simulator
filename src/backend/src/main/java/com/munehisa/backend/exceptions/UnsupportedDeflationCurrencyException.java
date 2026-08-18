package com.munehisa.backend.exceptions;

public class UnsupportedDeflationCurrencyException extends LocalizedRuntimeException {
    public UnsupportedDeflationCurrencyException(String baseCurrencyRaw) {
        super("error.unsupportedDeflationCurrency", baseCurrencyRaw);
    }
}
