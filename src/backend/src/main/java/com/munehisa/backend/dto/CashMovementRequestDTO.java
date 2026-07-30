package com.munehisa.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashMovementRequestDTO(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal amount,

        @NotNull
        Boolean todaysMoney
) {
}
