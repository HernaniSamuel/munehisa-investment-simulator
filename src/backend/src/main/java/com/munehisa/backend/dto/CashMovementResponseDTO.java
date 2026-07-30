package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CashMovementResponseDTO(
        UUID simulationId,
        BigDecimal appliedAmount,
        BigDecimal cashBalance,
        BigDecimal totalPatrimony,
        InflationDeflationResultDTO deflation
) {
}
