package com.munehisa.backend.controllers;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.dto.dataservice.RawAssetMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.infra.security.TokenService;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.AssetMonthlyPriceRepository;
import com.munehisa.backend.repository.ExchangeRateRepository;
import com.munehisa.backend.repository.PositionRepository;
import com.munehisa.backend.repository.SimulationRepository;
import com.munehisa.backend.repository.SnapshotPositionRepository;
import com.munehisa.backend.repository.SnapshotRepository;
import com.munehisa.backend.repository.TransactionRepository;
import com.munehisa.backend.service.DataServiceAssetClient;
import com.munehisa.backend.service.DataServiceExchangeRateClient;
import com.munehisa.backend.service.InflationDeflationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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
    @MockitoSpyBean
    private TransactionRepository transactionRepository;
    @Autowired
    private SnapshotRepository snapshotRepository;
    @Autowired
    private SnapshotPositionRepository snapshotPositionRepository;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private AssetCatalogRepository assetCatalogRepository;
    @Autowired
    private AssetMonthlyPriceRepository assetMonthlyPriceRepository;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @MockitoBean
    private InflationDeflationService inflationDeflationService;
    @MockitoBean
    private DataServiceAssetClient dataServiceAssetClient;
    @MockitoBean
    private DataServiceExchangeRateClient dataServiceExchangeRateClient;

    @AfterEach
    void cleanAssetCatalog() {
        // AssetCatalog/ExchangeRate aren't owned by a user, so IntegrationTestBase's
        // user-cascade cleanup never reaches them; several tests below persist rows
        // directly. Positions are deleted first since this @AfterEach runs before the
        // superclass's user-cascade cleanup, and some reset tests deliberately leave
        // positions in place to assert on.
        positionRepository.deleteAll();
        assetCatalogRepository.deleteAll();
        exchangeRateRepository.deleteAll();
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

    private Simulation seedSimulation(UUID userId, String name, String baseCurrency, BigDecimal cashBalance) {
        Simulation simulation = new Simulation();
        simulation.setUserId(userId);
        simulation.setName(name);
        simulation.setBaseCurrency(baseCurrency);
        simulation.setStartMonth(YearMonth.of(2024, 1));
        simulation.setCurrentMonth(YearMonth.of(2024, 1));
        simulation.setCashBalance(cashBalance);
        simulation.setTotalAssetValue(BigDecimal.ZERO);
        return simulationRepository.save(simulation);
    }

    private InflationDeflationResultDTO deflationResult(BigDecimal originalValue, BigDecimal deflatedValue, YearMonth targetMonth) {
        InflationLookupResultDTO currentMonthLookup = new InflationLookupResultDTO(
                InflationCurrency.BRL, YearMonth.of(2024, 1), YearMonth.of(2024, 1), new BigDecimal("200"), false, true);
        InflationLookupResultDTO targetMonthLookup = new InflationLookupResultDTO(
                InflationCurrency.BRL, targetMonth, targetMonth, new BigDecimal("100"), true, false);
        return new InflationDeflationResultDTO(originalValue, deflatedValue, InflationCurrency.BRL, currentMonthLookup, targetMonthLookup);
    }

    private String readBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private AssetCatalog seedAssetCatalog(String ticker, String name) {
        AssetCatalog asset = new AssetCatalog();
        asset.setTicker(ticker);
        asset.setName(name);
        asset.setBaseCurrency("USD");
        asset.setStartDate(LocalDate.of(2000, 1, 1));
        asset = assetCatalogRepository.save(asset);

        // AssetCacheService.getAssetSeries assumes a catalog row never exists without at
        // least one cached monthly price row (true whenever the pair is written together by
        // its own upsert flow); seeding just the catalog row directly would violate that.
        AssetMonthlyPrice price = new AssetMonthlyPrice();
        price.setTicker(ticker);
        price.setReferenceMonth(YearMonth.of(2024, 1));
        price.setOpen(new BigDecimal("180.00"));
        price.setHigh(new BigDecimal("180.00"));
        price.setLow(new BigDecimal("180.00"));
        price.setClose(new BigDecimal("180.00"));
        price.setVolume(1_000_000L);
        assetMonthlyPriceRepository.save(price);

        return asset;
    }

    private Position seedPosition(UUID simulationId, UUID assetId, long quantity, BigDecimal costBasis, BigDecimal dividends) {
        Position position = new Position();
        position.setSimulationId(simulationId);
        position.setAssetId(assetId);
        position.setQuantity(quantity);
        position.setWeight(new BigDecimal("1.0"));
        position.setCostBasis(costBasis);
        position.setTotalDividendsReceived(dividends);
        return positionRepository.save(position);
    }

    private Snapshot seedSnapshot(UUID simulationId, BigDecimal cashBalance, BigDecimal totalAssetValue) {
        Snapshot snapshot = new Snapshot();
        snapshot.setSimulationId(simulationId);
        snapshot.setCashBalance(cashBalance);
        snapshot.setTotalAssetValue(totalAssetValue);
        return snapshotRepository.save(snapshot);
    }

    private SnapshotPosition seedSnapshotPosition(UUID snapshotId, String ticker, String assetName, long quantity, BigDecimal costBasis, BigDecimal dividends) {
        SnapshotPosition snapshotPosition = new SnapshotPosition();
        snapshotPosition.setSnapshotId(snapshotId);
        snapshotPosition.setTicker(ticker);
        snapshotPosition.setAssetName(assetName);
        snapshotPosition.setQuantity(quantity);
        snapshotPosition.setWeight(new BigDecimal("1.0"));
        snapshotPosition.setCostBasis(costBasis);
        snapshotPosition.setTotalDividendsReceived(dividends);
        return snapshotPositionRepository.save(snapshotPosition);
    }

    private Transaction seedTransaction(UUID simulationId, TransactionType type, YearMonth month, String ticker, String assetName, Long quantity) {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulationId);
        transaction.setType(type);
        transaction.setMonth(month);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTicker(ticker);
        transaction.setAssetName(assetName);
        transaction.setQuantity(quantity);
        return transactionRepository.save(transaction);
    }

    private RawAssetSeries rawAssetSeries(String ticker, String name) {
        return new RawAssetSeries(ticker, name, "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 1), new BigDecimal("180.00"), new BigDecimal("180.00"),
                        new BigDecimal("180.00"), new BigDecimal("180.00"), 1_000_000L, null, null)));
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

    // --- searchAsset ------------------------------------------------------------------------

    @Test
    void searchAsset_sameCurrency_returns200WithSeriesAndUnconvertedCashBalance() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", simulation.getId(), "AAPL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAPL"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.series.length()").value(1))
                .andExpect(jsonPath("$.cashBalance.amount").value(1000.00))
                .andExpect(jsonPath("$.cashBalance.currency").value("USD"))
                .andExpect(jsonPath("$.cashBalance.wasConverted").value(false));
    }

    @Test
    void searchAsset_crossCurrency_returns200WithConvertedCashBalance() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(
                List.of(new RawExchangeMonthDataPoint(simulation.getCurrentMonth(),
                        new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"))));

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", simulation.getId(), "AAPL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.cashBalance.amount").value(200.00))
                .andExpect(jsonPath("$.cashBalance.currency").value("USD"))
                .andExpect(jsonPath("$.cashBalance.wasConverted").value(true));
    }

    @Test
    void searchAsset_unknownTicker_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        when(dataServiceAssetClient.fetchSeries("ZZZZ")).thenThrow(new AssetNotFoundException("ZZZZ", new RuntimeException("404")));

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", simulation.getId(), "ZZZZ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchAsset_tickerPredatesStartDate_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        simulation.setCurrentMonth(YearMonth.of(1992, 1));
        simulation = simulationRepository.save(simulation);
        seedAssetCatalog("AAPL", "Apple Inc.");

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", simulation.getId(), "AAPL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchAsset_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", UUID.randomUUID(), "AAPL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchAsset_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD");

        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", simulation.getId(), "AAPL")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchAsset_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations/{id}/assets/{ticker}", UUID.randomUUID(), "AAPL"))
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

    // --- deposit --------------------------------------------------------------------------

    @Test
    void deposit_todaysMoneyFalse_returns200AppliesRawAmountAndRecordsTransaction() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("200.00"), false);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(200.00))
                .andExpect(jsonPath("$.cashBalance").value(200.00))
                .andExpect(jsonPath("$.deflation").doesNotExist());

        assertEquals(0, new BigDecimal("200.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.DEPOSIT, transactions.get(0).getType());
        assertEquals(0, new BigDecimal("200.00").compareTo(transactions.get(0).getAmount()));
    }

    @Test
    void deposit_todaysMoneyTrue_appliesDeflatedAmountAndSurfacesFallbackFlags() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        when(inflationDeflationService.deflate(new BigDecimal("300.00"), "BRL", simulation.getCurrentMonth()))
                .thenReturn(deflationResult(new BigDecimal("300.00"), new BigDecimal("250.00"), simulation.getCurrentMonth()));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("300.00"), true);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(250.00))
                .andExpect(jsonPath("$.cashBalance").value(250.00))
                .andExpect(jsonPath("$.deflation.targetMonthLookup.oldestAvailable").value(true))
                .andExpect(jsonPath("$.deflation.targetMonthLookup.newestAvailable").value(false));

        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(0, new BigDecimal("250.00").compareTo(transactions.get(0).getAmount()));
    }

    @Test
    void deposit_zeroAmount_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        CashMovementRequestDTO body = new CashMovementRequestDTO(BigDecimal.ZERO, false);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("-50.00"), false);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_missingTodaysMoney_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("100.00"), false);

        mockMvc.perform(post("/simulations/{id}/deposits", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_anotherUsersSimulation_returns404AndDoesNotChangeBalance() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("100.00"), false);

        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        assertEquals(0, BigDecimal.ZERO.compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void deposit_withoutToken_returns401() throws Exception {
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("100.00"), false);

        mockMvc.perform(post("/simulations/{id}/deposits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- withdrawal -------------------------------------------------------------------------

    @Test
    void withdrawal_todaysMoneyFalse_returns200AppliesRawAmountAndRecordsTransaction() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("400.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(400.00))
                .andExpect(jsonPath("$.cashBalance").value(600.00))
                .andExpect(jsonPath("$.deflation").doesNotExist());

        assertEquals(0, new BigDecimal("600.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.WITHDRAWAL, transactions.get(0).getType());
        assertEquals(0, new BigDecimal("400.00").compareTo(transactions.get(0).getAmount()));
    }

    @Test
    void withdrawal_todaysMoneyTrue_appliesDeflatedAmount() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        when(inflationDeflationService.deflate(new BigDecimal("300.00"), "BRL", simulation.getCurrentMonth()))
                .thenReturn(deflationResult(new BigDecimal("300.00"), new BigDecimal("250.00"), simulation.getCurrentMonth()));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("300.00"), true);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(250.00))
                .andExpect(jsonPath("$.cashBalance").value(750.00));

        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(0, new BigDecimal("250.00").compareTo(transactions.get(0).getAmount()));
    }

    @Test
    void withdrawal_amountExceedsCashBalance_returns400AndDoesNotChangeBalanceOrRecordTransaction() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("100.00"));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("150.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("100.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void withdrawal_deflatedAmountExceedsCashBalance_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("100.00"));
        when(inflationDeflationService.deflate(new BigDecimal("150.00"), "BRL", simulation.getCurrentMonth()))
                .thenReturn(deflationResult(new BigDecimal("150.00"), new BigDecimal("200.00"), simulation.getCurrentMonth()));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("150.00"), true);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("100.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void withdrawal_zeroAmount_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("100.00"));
        CashMovementRequestDTO body = new CashMovementRequestDTO(BigDecimal.ZERO, false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawal_negativeAmount_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("100.00"));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("-10.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawal_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("50.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawal_anotherUsersSimulation_returns404AndDoesNotChangeBalance() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL", new BigDecimal("100.00"));
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("50.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        assertEquals(0, new BigDecimal("100.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void withdrawal_withoutToken_returns401() throws Exception {
        CashMovementRequestDTO body = new CashMovementRequestDTO(new BigDecimal("50.00"), false);

        mockMvc.perform(post("/simulations/{id}/withdrawals", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- snapshot ---------------------------------------------------------------------------

    @Test
    void snapshot_ownSimulation_returns204AndUpsertsSnapshotAndPositions() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        simulation.setTotalAssetValue(new BigDecimal("500.00"));
        simulation = simulationRepository.save(simulation);

        AssetCatalog asset = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), asset.getId(), 10, new BigDecimal("800.00"), new BigDecimal("5.00"));

        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Snapshot snapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(snapshot.getCashBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(snapshot.getTotalAssetValue()));

        List<SnapshotPosition> snapshotPositions = snapshotPositionRepository.findBySnapshotId(snapshot.getId());
        assertEquals(1, snapshotPositions.size());
        assertEquals("AAPL", snapshotPositions.get(0).getTicker());
        assertEquals("Apple Inc.", snapshotPositions.get(0).getAssetName());
        assertEquals(10, snapshotPositions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("800.00").compareTo(snapshotPositions.get(0).getCostBasis()));
    }

    @Test
    void snapshot_existingSnapshot_overwritesInPlaceAndReplacesOldPositions() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));

        Snapshot existingSnapshot = seedSnapshot(simulation.getId(), BigDecimal.ZERO, BigDecimal.ZERO);
        seedSnapshotPosition(existingSnapshot.getId(), "MSFT", "Microsoft Corp.", 3, new BigDecimal("300.00"), BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Snapshot updatedSnapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseThrow();
        assertEquals(existingSnapshot.getId(), updatedSnapshot.getId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(updatedSnapshot.getCashBalance()));
        assertTrue(snapshotPositionRepository.findBySnapshotId(updatedSnapshot.getId()).isEmpty());
    }

    @Test
    void snapshot_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(post("/simulations/{id}/snapshot", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void snapshot_anotherUsersSimulation_returns404AndDoesNotCreateSnapshot() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL");

        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertTrue(snapshotRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void snapshot_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/simulations/{id}/snapshot", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // --- reset ------------------------------------------------------------------------------

    @Test
    void reset_roundTrip_restoresExactPriorStateAndDeletesMonthNonDividendTransactions() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        simulation.setTotalAssetValue(new BigDecimal("500.00"));
        simulation = simulationRepository.save(simulation);

        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("800.00"), new BigDecimal("5.00"));

        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Changes made after the snapshot: a deposit, a dividend, buying a new asset with the deposit.
        simulation.setCashBalance(new BigDecimal("1100.00"));
        simulation.setTotalAssetValue(new BigDecimal("700.00"));
        simulationRepository.save(simulation);
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), msft.getId(), 4, new BigDecimal("200.00"), BigDecimal.ZERO);
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, simulation.getCurrentMonth(), null, null, null);
        seedTransaction(simulation.getId(), TransactionType.DIVIDEND, simulation.getCurrentMonth(), "AAPL", "Apple Inc.", null);
        seedTransaction(simulation.getId(), TransactionType.BUY, simulation.getCurrentMonth(), "MSFT", "Microsoft Corp.", 4L);

        MvcResult result = mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(1000.00))
                .andExpect(jsonPath("$.totalPatrimony").value(1500.00))
                .andReturn();
        SimulationResponseDTO response = objectMapper.readValue(readBody(result), SimulationResponseDTO.class);
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.cashBalance()));

        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(reloaded.getCashBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(reloaded.getTotalAssetValue()));

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(aapl.getId(), positions.get(0).getAssetId());
        assertEquals(10, positions.get(0).getQuantity());

        // MSFT was only bought after the snapshot and no position references it anywhere now.
        assertTrue(assetCatalogRepository.findByTicker("MSFT").isEmpty());

        List<Transaction> remainingTransactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, remainingTransactions.size());
        assertEquals(TransactionType.DIVIDEND, remainingTransactions.get(0).getType());
    }

    @Test
    void reset_evictedTicker_refetchesCatalogAndRecreatesPosition() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));

        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("800.00"), BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Simulate the asset having been evicted from the shared cache since the snapshot was taken.
        positionRepository.deleteAll(positionRepository.findBySimulationId(simulation.getId()));
        assetCatalogRepository.deleteById(aapl.getId());
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(rawAssetSeries("AAPL", "Apple Inc."));

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        AssetCatalog refetched = assetCatalogRepository.findByTicker("AAPL").orElseThrow();
        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(refetched.getId(), positions.get(0).getAssetId());
        assertEquals(10, positions.get(0).getQuantity());
    }

    @Test
    void reset_positionOpenedAfterSnapshot_evictsOrphanedAsset() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedSnapshot(simulation.getId(), simulation.getCashBalance(), simulation.getTotalAssetValue());

        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), msft.getId(), 4, new BigDecimal("200.00"), BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(assetCatalogRepository.findByTicker("MSFT").isEmpty());
    }

    @Test
    void reset_positionOpenedAfterSnapshot_doesNotEvictAssetStillHeldByAnotherSimulation() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedSnapshot(simulation.getId(), simulation.getCashBalance(), simulation.getTotalAssetValue());

        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), msft.getId(), 4, new BigDecimal("200.00"), BigDecimal.ZERO);

        User otherUser = createUser(u -> u.setEmail("grace@example.com"));
        Simulation otherSimulation = seedSimulation(otherUser.getId(), "Other plan", "BRL");
        seedPosition(otherSimulation.getId(), msft.getId(), 2, new BigDecimal("100.00"), BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(assetCatalogRepository.findByTicker("MSFT").isPresent());
    }

    @Test
    void reset_noSnapshot_returns400AndChangesNothing() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("1000.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void reset_nonexistentId_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(post("/simulations/{id}/reset", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void reset_anotherUsersSimulation_returns404AndDoesNotChangeBalance() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedSnapshot(simulation.getId(), BigDecimal.ZERO, BigDecimal.ZERO);

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertEquals(0, new BigDecimal("1000.00").compareTo(simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void reset_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/simulations/{id}/reset", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reset_failureMidOperation_rollsBackEverything() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));

        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("800.00"), BigDecimal.ZERO);
        mockMvc.perform(post("/simulations/{id}/snapshot", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Changes made after the snapshot that the (failed) reset must leave untouched.
        simulation.setCashBalance(new BigDecimal("2000.00"));
        simulationRepository.save(simulation);
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), msft.getId(), 4, new BigDecimal("200.00"), BigDecimal.ZERO);
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, simulation.getCurrentMonth(), null, null, null);

        doThrow(new RuntimeException("simulated mid-operation failure")).when(transactionRepository).deleteAll(anyList());

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("2000.00").compareTo(reloaded.getCashBalance()));

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(2, positions.size());
        assertTrue(positions.stream().anyMatch(p -> p.getAssetId().equals(msft.getId())));

        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.DEPOSIT, transactions.get(0).getType());
    }
}
