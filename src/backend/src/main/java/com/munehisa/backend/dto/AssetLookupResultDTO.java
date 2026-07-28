package com.munehisa.backend.dto;

import java.time.YearMonth;
import java.util.List;

public record AssetLookupResultDTO(
        String ticker,
        String name,
        String baseCurrency,
        YearMonth requestedMonth,
        YearMonth returnedMonth,
        boolean truncated,
        List<AssetMonthDataDTO> series
) {}
