package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.infra.RestErrorMessage;
import com.munehisa.backend.service.SimulationService;
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

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/simulations")
@Tag(name = "Simulation", description = "Create, list, rename and delete the authenticated user's simulations")
@SecurityRequirement(name = "bearerAuth")
public class SimulationController {
    private final SimulationService simulationService;

    @PostMapping
    @Operation(summary = "Create a simulation", description = "Creates a simulation owned by the authenticated user, with cash balance zero and current month equal to the start month.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Simulation created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, unsupported currency, or start month after the real current month",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<SimulationResponseDTO> createSimulation(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateSimulationRequestDTO createSimulationRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(simulationService.create(createSimulationRequest, user));
    }

    @GetMapping
    @Operation(summary = "List simulations", description = "Returns a summary of every simulation owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulations returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<List<SimulationResponseDTO>> listSimulations(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(simulationService.list(user));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a simulation", description = "Returns one simulation's own header fields, the same shape as the list summary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<SimulationResponseDTO> getSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(simulationService.get(id, user));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a simulation", description = "Renames the authenticated user's own simulation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation renamed"),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<SimulationResponseDTO> renameSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody RenameSimulationRequestDTO renameSimulationRequest
    ) {
        return ResponseEntity.ok(simulationService.rename(id, renameSimulationRequest, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a simulation", description = "Permanently deletes the authenticated user's own simulation, cascading to its positions, transactions and snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Simulation deleted", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<HttpStatus> deleteSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        simulationService.delete(id, user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
