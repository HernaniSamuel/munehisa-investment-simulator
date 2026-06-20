package com.munehisa.backend.controllers;

import com.munehisa.backend.dto.*;
import com.munehisa.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody LoginRequestDTO body){
        return ResponseEntity.ok(authService.login(body));
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody RegisterRequestDTO body) {
        return ResponseEntity.ok(authService.register(body));
    }

    @PostMapping("/resend-email")
    public ResponseEntity resendEmail(@RequestBody ResendEmailRequestDTO body) {
        return ResponseEntity.ok(authService.resendEmail(body));
    }

    @GetMapping("/verify")
    public ResponseEntity verify(@RequestParam String token) {
        return ResponseEntity.ok(authService.verify(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity forgotPassword(@RequestBody ResendEmailRequestDTO body) {
        return ResponseEntity.ok(authService.forgotPassword(body));
    }

    @PostMapping("/reset-password")
    public ResponseEntity resetPassword(@RequestBody ResetPasswordRequestDTO body) {
        return ResponseEntity.ok(authService.resetPassword(body));
    }
}
