package com.munehisa.backend.dto;

import java.time.Instant;

public record ResendEmailResponseDTO(
        String message,
        Instant resendAvailableAt
) {}
