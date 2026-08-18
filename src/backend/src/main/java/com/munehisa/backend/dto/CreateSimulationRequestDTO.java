package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

public record CreateSimulationRequestDTO(
        @Size(min = 1, max = 255)
        @NotBlank
        String name,

        @Pattern(regexp = "BRL|USD", message = "{validation.baseCurrency.mustBeBrlOrUsd}")
        @NotBlank
        String baseCurrency,

        @NotNull
        YearMonth startMonth
) {
}
