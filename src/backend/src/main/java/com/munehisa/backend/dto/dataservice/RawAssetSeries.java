package com.munehisa.backend.dto.dataservice;

import java.time.LocalDate;
import java.util.List;

public record RawAssetSeries(
        String ticker,
        String name,
        String baseCurrency,
        LocalDate startDate,
        List<RawAssetMonthDataPoint> monthlyData
) {}
