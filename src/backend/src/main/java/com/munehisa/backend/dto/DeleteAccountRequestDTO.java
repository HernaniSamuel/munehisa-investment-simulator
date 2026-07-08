package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequestDTO(
        @Size(min = 8, max = 255)
        @NotBlank
        String password
) {
}
