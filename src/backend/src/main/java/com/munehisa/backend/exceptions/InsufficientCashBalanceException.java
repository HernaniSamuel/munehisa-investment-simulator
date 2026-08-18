package com.munehisa.backend.exceptions;

import java.math.BigDecimal;

public class InsufficientCashBalanceException extends LocalizedRuntimeException {
    public InsufficientCashBalanceException(BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("error.insufficientCashBalance", requestedAmount, availableBalance);
    }
}
