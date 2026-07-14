package com.munehisa.backend.dto;

import java.time.Instant;

public record AccountLockedResponseDTO(
        String message,
        Instant lockedUntil
) {
}
