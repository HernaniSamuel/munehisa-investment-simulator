package com.munehisa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank
        String resetPasswordToken,

        @Size(min = 8, max = 255)
        String newPassword
) {
}
