package com.munehisa.backend.exceptions;

import java.math.BigDecimal;

public class InsufficientCashBalanceException extends RuntimeException {
    public InsufficientCashBalanceException(BigDecimal requestedAmount, BigDecimal availableBalance) {
        super("Withdrawal amount (" + requestedAmount + ") exceeds available cash balance (" + availableBalance + ")");
    }
}
