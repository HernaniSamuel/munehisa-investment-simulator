package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNameRequestDTO(
        @Size(min = 1, max = 255)
        @NotBlank
        String name
) {
}
