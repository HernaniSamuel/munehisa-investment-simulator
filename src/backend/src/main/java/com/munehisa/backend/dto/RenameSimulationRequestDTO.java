package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSimulationRequestDTO(
        @Size(min = 1, max = 255)
        @NotBlank
        String name
) {
}
