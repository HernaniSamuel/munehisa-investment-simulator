package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record AdvanceMonthResponseDTO(
        UUID simulationId,
        YearMonth currentMonth,
        BigDecimal cashBalance,
        BigDecimal totalAssetValue,
        BigDecimal totalPatrimony,
        List<AdvanceMonthPositionDTO> positions
) {
}
