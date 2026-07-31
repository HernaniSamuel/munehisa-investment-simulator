package com.munehisa.backend.dto;

import java.math.BigDecimal;

public record PositionResponseDTO(
        String ticker,
        String assetName,
        long quantity,
        BigDecimal costBasis,
        BigDecimal weight,
        BigDecimal totalDividendsReceived
) {
}
