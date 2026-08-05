package com.munehisa.backend.dto.dataservice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TickerSearchMatch(
        String ticker,
        String name,
        String exchange,
        @JsonProperty("asset_type") String assetType
) {}
