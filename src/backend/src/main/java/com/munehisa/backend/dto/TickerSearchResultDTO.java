package com.munehisa.backend.dto;

public record TickerSearchResultDTO(
        String ticker,
        String name,
        String exchange,
        String assetType
) {}
