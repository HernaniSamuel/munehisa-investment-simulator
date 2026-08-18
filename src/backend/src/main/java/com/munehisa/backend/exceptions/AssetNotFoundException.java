package com.munehisa.backend.exceptions;

public class AssetNotFoundException extends LocalizedRuntimeException {
    public AssetNotFoundException(String ticker, Throwable cause) {
        super("error.assetNotFound", cause, ticker);
    }
}
