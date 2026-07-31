package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.AssetSearchResponseDTO;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.dto.TradeRequestDTO;
import com.munehisa.backend.dto.TradeResponseDTO;
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
@Tag(name = "Simulation", description = "Create, list, rename, delete, and deposit/withdraw cash for the authenticated user's simulations")
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

    @GetMapping("/{id}/assets/{ticker}")
    @Operation(summary = "Search an asset within a simulation", description = "Looks up a ticker's bounded price/dividend/split history as of the authenticated user's simulation's current month, plus the simulation's cash balance converted to the asset's currency.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asset found"),
            @ApiResponse(responseCode = "400", description = "Ticker exists but its start date is after the simulation's current month",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller, or the ticker does not exist",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<AssetSearchResponseDTO> searchAsset(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @PathVariable String ticker
    ) {
        return ResponseEntity.ok(simulationService.searchAsset(id, ticker, user));
    }

    @PostMapping("/{id}/deposits")
    @Operation(summary = "Deposit into a simulation", description = "Adds cash to the authenticated user's simulation. When todaysMoney is true, the amount is deflated to the simulation's current month before being applied and recorded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit applied"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, non-positive amount, or unsupported deflation currency/target month",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<CashMovementResponseDTO> depositToSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequestDTO request
    ) {
        return ResponseEntity.ok(simulationService.deposit(id, request, user));
    }

    @PostMapping("/{id}/withdrawals")
    @Operation(summary = "Withdraw from a simulation", description = "Removes cash from the authenticated user's simulation. When todaysMoney is true, the amount is deflated to the simulation's current month before being applied and recorded; the deflated value is what is checked against the available cash balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal applied"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, non-positive amount, amount exceeding the cash balance, or unsupported deflation currency/target month",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<CashMovementResponseDTO> withdrawFromSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequestDTO request
    ) {
        return ResponseEntity.ok(simulationService.withdraw(id, request, user));
    }

    @PostMapping("/{id}/buy")
    @Operation(summary = "Buy an asset for a simulation", description = "Buys whole units of a ticker for the authenticated user's simulation at the current month's close price, converting cost to the simulation's base currency when the asset's currency differs, then recomputes every position's weight and the simulation's total asset value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase applied"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, ticker exists but its start date is after the simulation's current month, or insufficient cash",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller, or the ticker does not exist",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<TradeResponseDTO> buy(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody TradeRequestDTO request
    ) {
        return ResponseEntity.ok(simulationService.buy(id, request, user));
    }

    @PostMapping("/{id}/sell")
    @Operation(summary = "Sell an asset from a simulation", description = "Sells whole units of a ticker held in the authenticated user's simulation at the current month's close price, removing cost basis proportionally, crediting proceeds to cash, and recomputing every position's weight and the simulation's total asset value. A full sell deletes the position and triggers the shared asset cache's eviction check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sale applied"),
            @ApiResponse(responseCode = "400", description = "Invalid request body, or quantity exceeds the held amount",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<TradeResponseDTO> sell(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody TradeRequestDTO request
    ) {
        return ResponseEntity.ok(simulationService.sell(id, request, user));
    }

    @PostMapping("/{id}/snapshot")
    @Operation(summary = "Snapshot a simulation", description = "Captures the authenticated user's simulation's current cash balance, total asset value, and positions into its snapshot slot, overwriting whatever it held before.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Snapshot created", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<HttpStatus> snapshotSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        simulationService.createSnapshot(id, user);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/{id}/reset")
    @Operation(summary = "Reset a simulation to its snapshot", description = "Restores the authenticated user's simulation's cash balance, total asset value, and positions from its snapshot, and undoes the current month's trades, deposits, and withdrawals (dividends already credited are kept).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation reset"),
            @ApiResponse(responseCode = "400", description = "No snapshot exists yet for this simulation",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class))),
            @ApiResponse(responseCode = "404", description = "No simulation with this id owned by the caller",
                    content = @Content(schema = @Schema(implementation = RestErrorMessage.class)))
    })
    public ResponseEntity<SimulationResponseDTO> resetSimulation(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(simulationService.resetToSnapshot(id, user));
    }
}
