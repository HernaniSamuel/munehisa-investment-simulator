package com.munehisa.backend.exceptions;

import java.math.BigDecimal;

public class InsufficientCashForPurchaseException extends LocalizedRuntimeException {
    public InsufficientCashForPurchaseException(BigDecimal requiredAmount, BigDecimal availableAmount, String currency) {
        super("error.insufficientCashForPurchase", requiredAmount, availableAmount, currency);
    }
}
