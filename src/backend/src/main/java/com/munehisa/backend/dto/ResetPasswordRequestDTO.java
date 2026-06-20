package com.munehisa.backend.dto;

public record ResetPasswordRequestDTO(String resetPasswordToken, String newPassword) {}
