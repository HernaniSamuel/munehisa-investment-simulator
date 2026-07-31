package com.munehisa.backend.dto;

import com.munehisa.backend.domain.simulation.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record TradeResponseDTO(
        UUID simulationId,
        TransactionType type,
        PositionResponseDTO position,
        BigDecimal appliedAmount,
        BigDecimal cashBalance,
        BigDecimal totalAssetValue,
        BigDecimal totalPatrimony
) {
}
