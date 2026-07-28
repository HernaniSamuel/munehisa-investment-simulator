package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeMonthlyDataPoint(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close
) {}
