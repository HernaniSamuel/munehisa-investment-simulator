package com.munehisa.backend.dto.dataservice;

// No @JsonProperty needed: data-service serializes this field as "assetType" (issue #89's
// acceptance criteria specify that casing for this route specifically), unlike every other
// snake_case field the other data-service wire DTOs bridge (e.g. AssetSeriesResponse's
// base_currency/start_date).
public record TickerSearchMatch(
        String ticker,
        String name,
        String exchange,
        String assetType
) {}
