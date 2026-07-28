package com.munehisa.backend.exceptions;

public class AssetDataServiceException extends RuntimeException {
    public AssetDataServiceException(String ticker, Throwable cause) {
        super("Failed to fetch asset series for " + ticker + " from data-service", cause);
    }
}
