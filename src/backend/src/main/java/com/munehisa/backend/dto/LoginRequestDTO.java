package com.munehisa.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @Email
        @Size(max = 255)
        @NotBlank
        String email,

        @Size(min = 8, max = 255)
        String password
) {
}
