package com.munehisa.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendEmailRequestDTO(
        @Email
        @Size(max = 255)
        @NotBlank
        String email
) {
}
