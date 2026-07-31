package com.munehisa.backend.exceptions;

import java.math.BigDecimal;

public class InsufficientCashForPurchaseException extends RuntimeException {
    public InsufficientCashForPurchaseException(BigDecimal requiredAmount, BigDecimal availableAmount, String currency) {
        super("Purchase cost (" + requiredAmount + " " + currency + ") exceeds available cash balance ("
                + availableAmount + " " + currency + ")");
    }
}
