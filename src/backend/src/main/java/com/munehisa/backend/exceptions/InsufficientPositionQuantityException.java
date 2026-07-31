package com.munehisa.backend.exceptions;

public class InsufficientPositionQuantityException extends RuntimeException {
    public InsufficientPositionQuantityException(String ticker, long requestedQuantity, long heldQuantity) {
        super("Cannot sell " + requestedQuantity + " units of " + ticker + " - only " + heldQuantity + " held");
    }
}
