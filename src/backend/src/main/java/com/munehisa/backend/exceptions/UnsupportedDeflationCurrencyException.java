package com.munehisa.backend.exceptions;

public class UnsupportedDeflationCurrencyException extends RuntimeException {
    public UnsupportedDeflationCurrencyException(String baseCurrencyRaw) {
        super("Inflation deflation only supports BRL or USD as base currency, got: " + baseCurrencyRaw);
    }
}
