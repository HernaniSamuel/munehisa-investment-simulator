package com.munehisa.backend.exceptions;

public class AssetDataServiceException extends LocalizedRuntimeException {
    public AssetDataServiceException(String ticker, Throwable cause) {
        super("error.assetDataService", cause, ticker);
    }
}
