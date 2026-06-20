package com.munehisa.backend.dto;

import org.springframework.http.HttpStatus;

public record RegisterResponseDTO(HttpStatus httpStatus, String response) {
}
