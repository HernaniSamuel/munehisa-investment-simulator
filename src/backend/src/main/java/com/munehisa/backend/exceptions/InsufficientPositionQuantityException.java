package com.munehisa.backend.exceptions;

public class InsufficientPositionQuantityException extends LocalizedRuntimeException {
    public InsufficientPositionQuantityException(String ticker, long requestedQuantity, long heldQuantity) {
        super("error.insufficientPositionQuantity", requestedQuantity, ticker, heldQuantity);
    }
}
