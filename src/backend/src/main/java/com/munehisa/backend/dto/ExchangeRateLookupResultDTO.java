package com.munehisa.backend.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record ExchangeRateLookupResultDTO(
        String fromCurrency,
        String toCurrency,
        YearMonth requestedMonth,
        YearMonth returnedMonth,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        boolean oldestAvailable,
        boolean newestAvailable
) {}
