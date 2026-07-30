package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.infra.security.TokenService;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.PositionRepository;
import com.munehisa.backend.repository.SimulationRepository;
import com.munehisa.backend.repository.SnapshotPositionRepository;
import com.munehisa.backend.repository.SnapshotRepository;
import com.munehisa.backend.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class SimulationControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TokenService tokenService;
    @Autowired
    private SimulationRepository simulationRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private SnapshotRepository snapshotRepository;
    @Autowired
    private SnapshotPositionRepository snapshotPositionRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private AssetCatalogRepository assetCatalogRepository;

    @AfterEach
    void cleanAssetCatalog() {
        // AssetCatalog isn't owned by a user, so IntegrationTestBase's user-cascade
        // cleanup never reaches it; the cascade-delete test below persists one directly.
        assetCatalogRepository.deleteAll();
    }

    private Simulation seedSimulation(UUID userId, String name, String baseCurrency) {
        Simulation simulation = new Simulation();
        simulation.setUserId(userId);
        simulation.setName(name);
        simulation.setBaseCurrency(baseCurrency);
        simulation.setStartMonth(YearMonth.of(2024, 1));
        simulation.setCurrentMonth(YearMonth.of(2024, 1));
        simulation.setCashBalance(BigDecimal.ZERO);
        simulation.setTotalAssetValue(BigDecimal.ZERO);
        return simulationRepository.save(simulation);
    }

    private String readBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    // --- create -----------------------------------------------------------------------

    @Test
    void create_validRequest_returns201OwnedByCallerCashZero() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO("Retirement plan", "BRL", YearMonth.of(2024, 1));

        MvcResult result = mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Retirement plan"))
                .andExpect(jsonPath("$.baseCurrency").value("BRL"))
                .andExpect(jsonPath("$.cashBalance").value(0))
                .andExpect(jsonPath("$.totalPatrimony").value(0))
                .andReturn();

        SimulationResponseDTO response = objectMapper.readValue(readBody(result), SimulationResponseDTO.class);
        assertEquals(YearMonth.of(2024, 1), response.startMonth());
        assertEquals(response.startMonth(), response.currentMonth());

        List<Simulation> stored = simulationRepository.findByUserId(user.getId());
        assertEquals(1, stored.size());
        assertEquals(user.getId(), stored.get(0).getUserId());
    }

    @Test
    void create_futureStartMonth_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO(
                "Future plan", "BRL", YearMonth.now().plusMonths(2));

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertTrue(simulationRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void create_unsupportedCurrency_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO("Retirement plan", "EUR", YearMonth.of(2024, 1));

        mockMvc.perform(post("/simulations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertTrue(simulationRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        CreateSimulationRequestDTO body = new CreateSimulationRequestDTO("Retirement plan", "BRL", YearMonth.of(2024, 1));

        mockMvc.perform(post("/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- list ---------------------------------------------------------------------------

    @Test
    void list_returnsOnlyCallersOwnSimulations() throws Exception {
        User userA = createUser(u -> {
        });
        User userB = createUser(u -> u.setEmail("grace@example.com"));
        String tokenA = tokenService.generateToken(userA);

        seedSimulation(userA.getId(), "A's plan", "BRL");
        seedSimulation(userB.getId(), "B's plan", "USD");

        mockMvc.perform(get("/simulations").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("A's plan"));
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations"))
                .andExpect(status().isUnauthorized());
    }

    // --- get ------------------------------------------------------------------------------

    @Test
    void get_ownSimulation_returns200WithExpectedFields() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL");

        mockMvc.perform(get("/simulations/{id}", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(simulation.getId().toString()))
                .andExpect(jsonPath("$.name").value("Retirement plan"))
                .andExpect(jsonPath("$.baseCurrency").value("BRL"))
                .andExpect(jsonPath("$.cashBalance").value(0))
                .andExpect(jsonPath("$.totalPatrimony").value(0));
    }

    @Test
    void get_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(get("/simulations/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Simulation not found"));
    }

    @Test
    void get_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL");

        mockMvc.perform(get("/simulations/{id}", simulation.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Simulation not found"));
    }

    @Test
    void get_notFoundResponses_areIdenticalForNonexistentAndOtherUsersSimulation() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL");

        MvcResult nonexistentResult = mockMvc.perform(get("/simulations/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult notOwnedResult = mockMvc.perform(get("/simulations/{id}", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(nonexistentResult.getResponse().getStatus(), notOwnedResult.getResponse().getStatus());
        assertEquals(readBody(nonexistentResult), readBody(notOwnedResult));
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- rename ---------------------------------------------------------------------------

    @Test
    void rename_ownSimulation_returns200AndPersists() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Old name", "BRL");
        RenameSimulationRequestDTO body = new RenameSimulationRequestDTO("New name");

        mockMvc.perform(patch("/simulations/{id}", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"));

        assertEquals("New name", simulationRepository.findById(simulation.getId()).orElseThrow().getName());
    }

    @Test
    void rename_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        RenameSimulationRequestDTO body = new RenameSimulationRequestDTO("New name");

        mockMvc.perform(patch("/simulations/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rename_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Old name", "BRL");
        RenameSimulationRequestDTO body = new RenameSimulationRequestDTO("New name");

        mockMvc.perform(patch("/simulations/{id}", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        assertEquals("Old name", simulationRepository.findById(simulation.getId()).orElseThrow().getName());
    }

    @Test
    void rename_blankName_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Old name", "BRL");
        RenameSimulationRequestDTO body = new RenameSimulationRequestDTO("");

        mockMvc.perform(patch("/simulations/{id}", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rename_withoutToken_returns401() throws Exception {
        RenameSimulationRequestDTO body = new RenameSimulationRequestDTO("New name");

        mockMvc.perform(patch("/simulations/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    void delete_ownSimulation_returns204AndCascadesPositionTransactionSnapshot() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL");

        AssetCatalog asset = new AssetCatalog();
        asset.setTicker("AAPL");
        asset.setName("Apple Inc.");
        asset.setBaseCurrency("USD");
        asset.setStartDate(LocalDate.of(2000, 1, 1));
        asset = assetCatalogRepository.save(asset);

        Position position = new Position();
        position.setSimulationId(simulation.getId());
        position.setAssetId(asset.getId());
        position.setQuantity(10);
        position.setWeight(new BigDecimal("1.0"));
        position.setCostBasis(new BigDecimal("100.00"));
        position.setTotalDividendsReceived(BigDecimal.ZERO);
        positionRepository.save(position);

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setMonth(YearMonth.of(2024, 1));
        transaction.setAmount(new BigDecimal("1000.00"));
        transactionRepository.save(transaction);

        Snapshot snapshot = new Snapshot();
        snapshot.setSimulationId(simulation.getId());
        snapshot.setCashBalance(BigDecimal.ZERO);
        snapshot.setTotalAssetValue(BigDecimal.ZERO);
        snapshot = snapshotRepository.save(snapshot);

        SnapshotPosition snapshotPosition = new SnapshotPosition();
        snapshotPosition.setSnapshotId(snapshot.getId());
        snapshotPosition.setTicker("AAPL");
        snapshotPosition.setAssetName("Apple Inc.");
        snapshotPosition.setQuantity(10);
        snapshotPosition.setWeight(new BigDecimal("1.0"));
        snapshotPosition.setCostBasis(new BigDecimal("100.00"));
        snapshotPosition.setTotalDividendsReceived(BigDecimal.ZERO);
        snapshotPositionRepository.save(snapshotPosition);

        mockMvc.perform(delete("/simulations/{id}", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(simulationRepository.findById(simulation.getId()).isEmpty());
        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(snapshotRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(snapshotPositionRepository.findBySnapshotId(snapshot.getId()).isEmpty());
    }

    @Test
    void delete_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(delete("/simulations/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_anotherUsersSimulation_returns404AndDoesNotDelete() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL");

        mockMvc.perform(delete("/simulations/{id}", simulation.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertTrue(simulationRepository.findById(simulation.getId()).isPresent());
    }

    @Test
    void delete_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/simulations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
