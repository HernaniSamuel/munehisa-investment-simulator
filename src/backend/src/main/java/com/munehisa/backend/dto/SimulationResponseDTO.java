package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record SimulationResponseDTO(
        UUID id,
        String name,
        String baseCurrency,
        YearMonth startMonth,
        YearMonth currentMonth,
        BigDecimal cashBalance,
        BigDecimal totalPatrimony
) {
}
