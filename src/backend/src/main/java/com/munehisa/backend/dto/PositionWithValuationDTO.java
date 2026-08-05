package com.munehisa.backend.dto;

import java.math.BigDecimal;

public record PositionWithValuationDTO(
        String ticker,
        String assetName,
        long quantity,
        BigDecimal currentPrice,
        boolean wasTruncated,
        BigDecimal marketValue,
        BigDecimal weight,
        BigDecimal costBasis,
        BigDecimal gainAmount,
        BigDecimal gainPercent
) {
}
