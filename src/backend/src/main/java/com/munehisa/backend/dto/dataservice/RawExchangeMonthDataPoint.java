package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RawExchangeMonthDataPoint(
        YearMonth month,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close
) {}
