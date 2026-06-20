package com.munehisa.backend.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ResendEmailResponseDTO(HttpStatus httpStatus, String message, Instant resendAvailableAt) {}
