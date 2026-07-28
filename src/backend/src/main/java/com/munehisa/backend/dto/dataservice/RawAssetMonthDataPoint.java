package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RawAssetMonthDataPoint(
        YearMonth month,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal dividends,
        BigDecimal splits
) {}
