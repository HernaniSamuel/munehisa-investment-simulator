package com.munehisa.backend.dto;

import java.time.Instant;

public record ForgotPasswordResponseDTO(
    String message,
    Instant resendAvailableAt
) {}