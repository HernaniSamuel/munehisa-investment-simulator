package com.munehisa.backend.dto.dataservice;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RawInflationDataPoint(
        YearMonth month,
        BigDecimal rawValue
) {}
