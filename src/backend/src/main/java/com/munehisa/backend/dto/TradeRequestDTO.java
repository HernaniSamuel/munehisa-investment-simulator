package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TradeRequestDTO(
        @NotBlank
        String ticker,

        @NotNull
        @Positive
        Long quantity
) {
}
