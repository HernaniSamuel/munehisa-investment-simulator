package com.munehisa.backend.dto.dataservice;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record BrlInflationSeriesResponse(
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("monthly_data") List<BrlInflationMonthDataPoint> monthlyData
) {}
