package com.munehisa.backend.dto;

import com.munehisa.backend.domain.inflation.InflationCurrency;

import java.math.BigDecimal;

public record InflationDeflationResultDTO(
        BigDecimal originalValue,
        BigDecimal deflatedValue,
        InflationCurrency currency,
        InflationLookupResultDTO currentMonthLookup,
        InflationLookupResultDTO targetMonthLookup
) {}
