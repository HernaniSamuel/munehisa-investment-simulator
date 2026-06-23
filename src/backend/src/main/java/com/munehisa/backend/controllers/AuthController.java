package com.munehisa.backend.controllers;

import com.munehisa.backend.dto.*;
import com.munehisa.backend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/resend-email")
    public ResponseEntity<ResendEmailResponseDTO> resendEmail(@Valid @RequestBody ResendEmailRequestDTO request) {
        Optional<ResendEmailResponseDTO> result = authService.resendEmail(request);

        return result.map(resendEmailResponseDTO -> ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(resendEmailResponseDTO))
                .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @GetMapping("/verify")
    public ResponseEntity<LoginResponseDTO> verify(@RequestParam @NotBlank String verificationToken) {
        return ResponseEntity.ok(authService.verify(verificationToken));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO forgotPasswordRequest) {
        return ResponseEntity.ok(authService.forgotPassword(forgotPasswordRequest));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<LoginResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO resetPasswordRequest) {
        return ResponseEntity.ok(authService.resetPassword(resetPasswordRequest));
    }
}
