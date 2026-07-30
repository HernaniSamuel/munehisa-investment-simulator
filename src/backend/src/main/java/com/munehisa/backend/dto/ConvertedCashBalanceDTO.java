package com.munehisa.backend.dto;

import java.math.BigDecimal;

public record ConvertedCashBalanceDTO(
        BigDecimal amount,
        String currency,
        boolean wasConverted
) {}
