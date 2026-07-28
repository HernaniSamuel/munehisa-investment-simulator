package com.munehisa.backend.dto;

import com.munehisa.backend.domain.inflation.InflationCurrency;

import java.math.BigDecimal;
import java.time.YearMonth;

public record InflationLookupResultDTO(
        InflationCurrency currency,
        YearMonth requestedMonth,
        YearMonth returnedMonth,
        BigDecimal accumulatedIndex,
        boolean oldestAvailable,
        boolean newestAvailable
) {}
