package com.munehisa.backend.exceptions;

public class AssetUnavailableException extends RuntimeException {
    public AssetUnavailableException(String ticker, Throwable cause) {
        super("No cached asset data available for " + ticker + " and refresh from data-service failed", cause);
    }
}
