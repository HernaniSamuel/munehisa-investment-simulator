package com.munehisa.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @Size(min = 1, max = 30)
        @NotBlank
        String name,

        @Email
        @NotBlank
        String email,

        @Size(min = 8, max = 128)
        String password
) {}
