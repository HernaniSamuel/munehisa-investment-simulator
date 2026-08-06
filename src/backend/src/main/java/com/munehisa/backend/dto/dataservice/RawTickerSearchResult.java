package com.munehisa.backend.dto.dataservice;

public record RawTickerSearchResult(
        String ticker,
        String name,
        String exchange,
        String assetType
) {}
