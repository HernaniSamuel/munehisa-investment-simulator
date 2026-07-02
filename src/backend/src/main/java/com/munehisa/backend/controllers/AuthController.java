package com.munehisa.backend.controllers;

import com.munehisa.backend.dto.*;
import com.munehisa.backend.infra.RestErrorMessage;
import com.munehisa.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Registration, login, email verification and password recovery")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates a verified user with email and password and returns a JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated successfully, JWT returned"),
            @ApiResponse(responseCode = "401", description = "Unknown email or wrong password",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "403", description = "Email not verified yet",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates an unverified user and sends a verification email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created, verification email sent", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email already registered and verified",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the verification email", description = "Issues a new verification token, unless one is already pending.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "New verification email sent", content = @Content),
            @ApiResponse(responseCode = "429", description = "A verification email was already sent recently"),
            @ApiResponse(responseCode = "401", description = "Unknown email",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<ResendEmailResponseDTO> resendEmail(@Valid @RequestBody ResendEmailRequestDTO request) {
        Optional<ResendEmailResponseDTO> result = authService.resendEmail(request);

        return result.map(resendEmailResponseDTO -> ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(resendEmailResponseDTO))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify an email address", description = "Confirms a user's email using the token sent by the verification email and returns a JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified, JWT returned"),
            @ApiResponse(responseCode = "400", description = "Token is invalid, expired, or already used",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<LoginResponseDTO> verify(@RequestParam @NotBlank String verificationToken) {
        return ResponseEntity.ok(authService.verify(verificationToken));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset", description = "Issues a password reset token, unless one is already pending.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password recovery email sent", content = @Content),
            @ApiResponse(responseCode = "429", description = "A password recovery email was already sent recently"),
            @ApiResponse(responseCode = "401", description = "Unknown email",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO forgotPasswordRequest) {
        Optional<ForgotPasswordResponseDTO> result = authService.forgotPassword(forgotPasswordRequest);

        return result.map(forgotPasswordResponseDTO -> ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(forgotPasswordResponseDTO))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Confirm a password reset", description = "Sets a new password using the token sent by the password recovery email and returns a JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated, JWT returned"),
            @ApiResponse(responseCode = "400", description = "Token is invalid, expired, or already used",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<LoginResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO resetPasswordRequest) {
        return ResponseEntity.ok(authService.resetPassword(resetPasswordRequest));
    }
}
