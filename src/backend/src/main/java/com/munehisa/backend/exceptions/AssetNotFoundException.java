package com.munehisa.backend.exceptions;

public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String ticker, Throwable cause) {
        super("Unknown ticker " + ticker + " - not found by the data-service", cause);
    }
}
