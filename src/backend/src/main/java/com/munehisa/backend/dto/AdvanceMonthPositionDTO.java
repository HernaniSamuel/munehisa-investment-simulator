package com.munehisa.backend.dto;

import java.math.BigDecimal;

public record AdvanceMonthPositionDTO(
        String ticker,
        String assetName,
        long quantity,
        BigDecimal price,
        boolean wasTruncated,
        BigDecimal dividendReceived,
        BigDecimal weight,
        BigDecimal costBasis,
        BigDecimal totalDividendsReceived
) {
}
