package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetMonthlyDataPoint(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal dividends,
        BigDecimal splits
) {}
