package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.DeleteAccountRequestDTO;
import com.munehisa.backend.dto.UpdateNameRequestDTO;
import com.munehisa.backend.dto.UpdateNameResponseDTO;
import com.munehisa.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<String> getUser() {
        return ResponseEntity.ok("success");
    }

    @PatchMapping
    public ResponseEntity<UpdateNameResponseDTO> updateUserName(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateNameRequestDTO updateNameRequest
    ) {
        return ResponseEntity.ok(userService.updateName(updateNameRequest, user));
    }

    @PostMapping("/delete")
    public ResponseEntity<HttpStatus> deleteUserAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteAccountRequestDTO deleteAccountRequest
    ) {
        userService.deleteUserAccount(deleteAccountRequest, user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
