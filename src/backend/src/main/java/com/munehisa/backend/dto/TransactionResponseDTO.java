package com.munehisa.backend.dto;

import com.munehisa.backend.domain.simulation.TransactionType;

import java.math.BigDecimal;
import java.time.YearMonth;

public record TransactionResponseDTO(
        TransactionType type,
        YearMonth month,
        BigDecimal amount,
        String ticker,
        String assetName,
        BigDecimal quantity
) {
}
