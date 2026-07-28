package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UsdInflationMonthDataPoint(
        LocalDate date,
        BigDecimal value
) {}
