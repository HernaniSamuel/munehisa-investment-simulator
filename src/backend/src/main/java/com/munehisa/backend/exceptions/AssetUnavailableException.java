package com.munehisa.backend.exceptions;

public class AssetUnavailableException extends LocalizedRuntimeException {
    public AssetUnavailableException(String ticker, Throwable cause) {
        super("error.assetUnavailable", cause, ticker);
    }
}
