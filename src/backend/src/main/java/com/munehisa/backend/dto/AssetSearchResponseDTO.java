package com.munehisa.backend.dto;

import java.time.YearMonth;
import java.util.List;

public record AssetSearchResponseDTO(
        String ticker,
        String name,
        String currency,
        YearMonth requestedMonth,
        YearMonth returnedMonth,
        boolean truncated,
        List<AssetMonthDataDTO> series,
        ConvertedCashBalanceDTO cashBalance
) {}
