package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.AccountLockedResponseDTO;
import com.munehisa.backend.dto.DeleteAccountRequestDTO;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.infra.RestErrorMessage;
import com.munehisa.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/user")
@Tag(name = "User", description = "Authenticated user profile management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Check session", description = "Confirms the caller's JWT is valid; used by the frontend to check whether a stored session is still usable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<String> getUser() {
        return ResponseEntity.ok("success");
    }

    @PatchMapping
    @Operation(summary = "Update display name", description = "Changes the authenticated user's name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Name updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<UpdateNameResponseDTO> updateUserName(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateNameRequestDTO updateNameRequest
    ) {
        return ResponseEntity.ok(userService.updateName(updateNameRequest, user));
    }

    @PostMapping("/delete")
    @Operation(summary = "Delete account", description = "Permanently deletes the authenticated user's account after confirming their password.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing/invalid token, or wrong password",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "429", description = "Account temporarily locked after too many failed attempts",
                    content = @Content(schema = @Schema(implementation = AccountLockedResponseDTO.class)))
    })
    public ResponseEntity<HttpStatus> deleteUserAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteAccountRequestDTO deleteAccountRequest
    ) {
        userService.deleteUserAccount(deleteAccountRequest, user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
