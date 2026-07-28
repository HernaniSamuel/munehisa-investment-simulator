package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record AssetMonthDataDTO(
        YearMonth referenceMonth,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal dividends,
        BigDecimal splits
) {}
