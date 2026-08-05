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
import com.munehisa.backend.dto.AdvanceMonthResponseDTO;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.dto.TradeRequestDTO;
import com.munehisa.backend.dto.TransactionResponseDTO;
import com.munehisa.backend.dto.dataservice.RawAssetMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawTickerSearchResult;
import com.munehisa.backend.exceptions.AssetDataServiceException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.TickerSearchUnavailableException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
    @MockitoSpyBean
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
        return seedAssetCatalog(ticker, name, new BigDecimal("180.00"));
    }

    private AssetCatalog seedAssetCatalog(String ticker, String name, BigDecimal closePrice) {
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
        price.setOpen(closePrice);
        price.setHigh(closePrice);
        price.setLow(closePrice);
        price.setClose(closePrice);
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

    private Transaction seedTransaction(UUID simulationId, TransactionType type, YearMonth month, String ticker, String assetName, BigDecimal quantity) {
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
                .andExpect(jsonPath("$[0].name").value("A's plan"))
                // Issue #84 added GET /simulations/{id}/positions as a separate endpoint rather
                // than extending this response shape - SimulationResponseDTO must stay as-is.
                .andExpect(jsonPath("$[0].positions").doesNotExist())
                .andExpect(jsonPath("$[0].totalGainAmount").doesNotExist())
                .andExpect(jsonPath("$[0].totalGainPercent").doesNotExist());
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
                .andExpect(jsonPath("$.totalPatrimony").value(0))
                // Issue #84 added GET /simulations/{id}/positions as a separate endpoint rather
                // than extending this response shape - SimulationResponseDTO must stay as-is.
                .andExpect(jsonPath("$.positions").doesNotExist())
                .andExpect(jsonPath("$.totalGainAmount").doesNotExist())
                .andExpect(jsonPath("$.totalGainPercent").doesNotExist());
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

    // --- searchTickers ----------------------------------------------------------------------

    @Test
    void searchTickers_returns200WithResultsFromDataService() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        when(dataServiceAssetClient.searchTickers("petr4")).thenReturn(
                List.of(new RawTickerSearchResult("PETR4.SA", "Petrobras", "SAO", "EQUITY")));

        mockMvc.perform(get("/simulations/{id}/assets/search", simulation.getId())
                        .param("query", "petr4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("PETR4.SA"))
                .andExpect(jsonPath("$[0].name").value("Petrobras"))
                .andExpect(jsonPath("$[0].exchange").value("SAO"))
                .andExpect(jsonPath("$[0].assetType").value("EQUITY"));
    }

    @Test
    void searchTickers_forwardsQueryToDataServiceUnmodified() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        when(dataServiceAssetClient.searchTickers("petr4")).thenReturn(List.of());

        mockMvc.perform(get("/simulations/{id}/assets/search", simulation.getId())
                        .param("query", "petr4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(dataServiceAssetClient).searchTickers("petr4");
    }

    @Test
    void searchTickers_noMatches_returns200WithEmptyArray() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        when(dataServiceAssetClient.searchTickers("zzz")).thenReturn(List.of());

        mockMvc.perform(get("/simulations/{id}/assets/search", simulation.getId())
                        .param("query", "zzz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchTickers_dataServiceUnavailable_returns503() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        when(dataServiceAssetClient.searchTickers("petr4"))
                .thenThrow(new TickerSearchUnavailableException(new RuntimeException("502")));

        mockMvc.perform(get("/simulations/{id}/assets/search", simulation.getId())
                        .param("query", "petr4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void searchTickers_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(get("/simulations/{id}/assets/search", UUID.randomUUID())
                        .param("query", "petr4")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchTickers_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD");

        mockMvc.perform(get("/simulations/{id}/assets/search", simulation.getId())
                        .param("query", "petr4")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchTickers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations/{id}/assets/search", UUID.randomUUID()).param("query", "petr4"))
                .andExpect(status().isUnauthorized());
    }

    // --- listPositions ----------------------------------------------------------------------

    @Test
    void listPositions_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(get("/simulations/{id}/positions", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPositions_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD");

        mockMvc.perform(get("/simulations/{id}/positions", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPositions_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations/{id}/positions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPositions_worked_buyAdvanceBuy_gainRemainsAnchoredToPreRebuySnapshot() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("5000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.", new BigDecimal("10.00"));

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TradeRequestDTO("AAPL", 10L))))
                .andExpect(status().isOk());

        // seedAssetCatalog only cached January 2024 at 10.00; advancing to February forces a
        // refresh, stubbed here to 10.50 - advanceMonth's own auto-snapshot then captures
        // qty=10/costBasis=100.00 as the baseline before the second buy below.
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("10.50"), new BigDecimal("10.50"),
                        new BigDecimal("10.50"), new BigDecimal("10.50"), 1_000_000L, null, null))));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Same month as the snapshot: buying 100 more shares at 10.50 brings the live position
        // to quantity=110/costBasis=1150.00 (100.00 + 100 * 10.50), but must not dilute the
        // gain already anchored to the pre-rebuy snapshot (qty=10/costBasis=100.00).
        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TradeRequestDTO("AAPL", 100L))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/simulations/{id}/positions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].quantity").value(110))
                .andExpect(jsonPath("$.positions[0].currentPrice").value(10.50))
                .andExpect(jsonPath("$.positions[0].wasTruncated").value(false))
                // marketValue = 110 * 10.50 = 1155.00 (full live quantity, unlike the gain below).
                .andExpect(jsonPath("$.positions[0].marketValue").value(1155.00))
                // costBasis is the raw, live Position.costBasis - unmodified by the snapshot baseline.
                .andExpect(jsonPath("$.positions[0].costBasis").value(1150.00))
                // baselineQty = min(110, 10) = 10; gainAmount = 10 * 10.50 - 100.00 = 5.00.
                .andExpect(jsonPath("$.positions[0].gainAmount").value(5.00))
                .andExpect(jsonPath("$.positions[0].gainPercent").value(0.05))
                .andExpect(jsonPath("$.totalAssetValue").value(1155.00))
                .andExpect(jsonPath("$.totalGainAmount").value(5.00))
                .andExpect(jsonPath("$.totalGainPercent").value(0.05));
    }

    @Test
    void listPositions_multiplePositions_totalsSumAcrossPositions() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.", new BigDecimal("100.00"));
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.", new BigDecimal("50.00"));
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("800.00"), BigDecimal.ZERO);
        seedPosition(simulation.getId(), msft.getId(), 10, new BigDecimal("400.00"), BigDecimal.ZERO);
        Snapshot snapshot = seedSnapshot(simulation.getId(), BigDecimal.ZERO, new BigDecimal("1200.00"));
        seedSnapshotPosition(snapshot.getId(), "AAPL", "Apple Inc.", 10, new BigDecimal("800.00"), BigDecimal.ZERO);
        seedSnapshotPosition(snapshot.getId(), "MSFT", "Microsoft Corp.", 10, new BigDecimal("400.00"), BigDecimal.ZERO);

        // AAPL: marketValue = 1000.00, gainAmount = 1000.00 - 800.00 = 200.00.
        // MSFT: marketValue = 500.00, gainAmount = 500.00 - 400.00 = 100.00.
        // totals: totalAssetValue = 1500.00, totalGainAmount = 300.00, totalGainPercent = 300.00 / 1200.00 = 0.25.
        mockMvc.perform(get("/simulations/{id}/positions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.totalAssetValue").value(1500.00))
                .andExpect(jsonPath("$.totalGainAmount").value(300.00))
                .andExpect(jsonPath("$.totalGainPercent").value(0.25));
    }

    @Test
    void listPositions_doesNotMutatePersistedWeightOrTotalAssetValue() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        simulation.setTotalAssetValue(new BigDecimal("123.45"));
        simulation = simulationRepository.save(simulation);
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.", new BigDecimal("180.00"));
        Position position = seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        position.setWeight(new BigDecimal("0.42"));
        positionRepository.save(position);

        // Live market value (5 * 180.00 = 900.00) differs from the persisted totalAssetValue
        // (123.45) on purpose, so a silent overwrite would be caught by the assertions below.
        mockMvc.perform(get("/simulations/{id}/positions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssetValue").value(900.00));

        Simulation reloadedSimulation = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("123.45").compareTo(reloadedSimulation.getTotalAssetValue()));

        Position reloadedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("0.42").compareTo(reloadedPosition.getWeight()));
    }

    // --- listTransactions --------------------------------------------------------------------

    @Test
    void listTransactions_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(get("/simulations/{id}/transactions", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listTransactions_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD");

        mockMvc.perform(get("/simulations/{id}/transactions", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listTransactions_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/simulations/{id}/transactions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTransactions_noTransactions_returnsEmptyList() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");

        mockMvc.perform(get("/simulations/{id}/transactions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listTransactions_returnsExactFieldsForEachTransactionType() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        YearMonth month = simulation.getCurrentMonth();
        // All seeded in the same month, so response order (created_at ascending) matches this
        // seed order - letting each row be asserted by index below.
        seedTransaction(simulation.getId(), TransactionType.BUY, month, "AAPL", "Apple Inc.", BigDecimal.valueOf(10));
        seedTransaction(simulation.getId(), TransactionType.SELL, month, "AAPL", "Apple Inc.", BigDecimal.valueOf(4));
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, month, null, null, null);
        seedTransaction(simulation.getId(), TransactionType.WITHDRAWAL, month, null, null, null);
        seedTransaction(simulation.getId(), TransactionType.DIVIDEND, month, "AAPL", "Apple Inc.", null);

        mockMvc.perform(get("/simulations/{id}/transactions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].type").value("BUY"))
                .andExpect(jsonPath("$[0].month").value(month.toString()))
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].assetName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[1].type").value("SELL"))
                .andExpect(jsonPath("$[1].ticker").value("AAPL"))
                .andExpect(jsonPath("$[1].assetName").value("Apple Inc."))
                .andExpect(jsonPath("$[1].quantity").value(4))
                .andExpect(jsonPath("$[2].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[2].amount").value(100.00))
                .andExpect(jsonPath("$[2].ticker").doesNotExist())
                .andExpect(jsonPath("$[2].assetName").doesNotExist())
                .andExpect(jsonPath("$[2].quantity").doesNotExist())
                .andExpect(jsonPath("$[3].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[3].ticker").doesNotExist())
                .andExpect(jsonPath("$[3].assetName").doesNotExist())
                .andExpect(jsonPath("$[3].quantity").doesNotExist())
                .andExpect(jsonPath("$[4].type").value("DIVIDEND"))
                .andExpect(jsonPath("$[4].ticker").value("AAPL"))
                .andExpect(jsonPath("$[4].assetName").value("Apple Inc."))
                .andExpect(jsonPath("$[4].quantity").doesNotExist());
    }

    @Test
    void listTransactions_orderedByMonthDescending() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        // Seeded out of order on purpose - the response order must come from the query, not insertion order.
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, YearMonth.of(2024, 3), null, null, null);
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, YearMonth.of(2024, 6), null, null, null);
        seedTransaction(simulation.getId(), TransactionType.DEPOSIT, YearMonth.of(2024, 1), null, null, null);

        mockMvc.perform(get("/simulations/{id}/transactions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2024-06"))
                .andExpect(jsonPath("$[1].month").value("2024-03"))
                .andExpect(jsonPath("$[2].month").value("2024-01"));
    }

    @Test
    void listTransactions_sameMonth_orderedByCreatedAtAscending() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD");
        YearMonth month = simulation.getCurrentMonth();
        Transaction first = seedTransaction(simulation.getId(), TransactionType.DEPOSIT, month, null, null, null);
        Transaction second = seedTransaction(simulation.getId(), TransactionType.WITHDRAWAL, month, null, null, null);

        mockMvc.perform(get("/simulations/{id}/transactions", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value(first.getType().name()))
                .andExpect(jsonPath("$[1].type").value(second.getType().name()));
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

    // --- buy ------------------------------------------------------------------------------

    @Test
    void buy_sameCurrency_returns200DeductsCashAndCreatesPosition() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("2000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 5L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(900.00))
                .andExpect(jsonPath("$.cashBalance").value(1100.00))
                .andExpect(jsonPath("$.position.ticker").value("AAPL"))
                .andExpect(jsonPath("$.position.quantity").value(5));

        assertEquals(0, new BigDecimal("1100.00").compareTo(
                simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(5, positions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("900.00").compareTo(positions.get(0).getCostBasis()));
        assertEquals(0, BigDecimal.ONE.compareTo(positions.get(0).getWeight()));

        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.BUY, transactions.get(0).getType());
        assertEquals(0, new BigDecimal("900.00").compareTo(transactions.get(0).getAmount()));
        assertEquals(0, new BigDecimal("5").compareTo(transactions.get(0).getQuantity()));
    }

    @Test
    void buy_crossCurrency_returns200ConvertsCostReconcilingCashAndCostBasis() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(
                List.of(new RawExchangeMonthDataPoint(simulation.getCurrentMonth(),
                        new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"))));
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // costInAssetCurrency = 180.00 USD; convertedCash check = 1000.00 * 0.20 = 200.00 USD
        // (passes); costInBaseCurrency = 180.00 * (1 / 0.20) = 900.00 BRL - the single value
        // reused below for the cash deduction, the cost basis, and the transaction amount.
        BigDecimal cashDeducted = new BigDecimal("1000.00").subtract(
                simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance());
        assertEquals(0, new BigDecimal("900.00").compareTo(cashDeducted));

        Position position = positionRepository.findBySimulationId(simulation.getId()).get(0);
        assertEquals(0, cashDeducted.compareTo(position.getCostBasis()));

        Transaction transaction = transactionRepository.findBySimulationId(simulation.getId()).get(0);
        assertEquals(0, cashDeducted.compareTo(transaction.getAmount()));
    }

    @Test
    void buy_crossCurrencyInsufficientFunds_returns400AndDoesNotChangeState() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", new BigDecimal("1000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(
                List.of(new RawExchangeMonthDataPoint(simulation.getCurrentMonth(),
                        new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"))));
        // convertedCash = 1000.00 * 0.20 = 200.00 USD; cost = 180.00 * 2 = 360.00 USD > 200.00.
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 2L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void buy_unknownTicker_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        when(dataServiceAssetClient.fetchSeries("ZZZZ")).thenThrow(new AssetNotFoundException("ZZZZ", new RuntimeException("404")));
        TradeRequestDTO body = new TradeRequestDTO("ZZZZ", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void buy_tickerPredatesStartDate_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        simulation.setCurrentMonth(YearMonth.of(1992, 1));
        simulation = simulationRepository.save(simulation);
        seedAssetCatalog("AAPL", "Apple Inc.");
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buy_existingTicker_updatesSamePositionRowNoDuplicate() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("5000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        Position existing = seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 5L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(existing.getId(), positions.get(0).getId());
        assertEquals(10, positions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("1800.00").compareTo(positions.get(0).getCostBasis()));
    }

    @Test
    void buy_withExistingUntouchedPosition_recalculatesItsWeightToo() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("5000.00"));
        seedAssetCatalog("AAPL", "Apple Inc.");
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), msft.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 5L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssetValue").value(1800.00));

        // MSFT value = 180.00 * 5 = 900.00; AAPL value = 180.00 * 5 = 900.00; total = 1800.00.
        Position reloadedMsft = positionRepository.findBySimulationId(simulation.getId()).stream()
                .filter(p -> p.getAssetId().equals(msft.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.5").compareTo(reloadedMsft.getWeight()));
    }

    @Test
    void buy_nonPositiveQuantity_returns400() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 0L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buy_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void buy_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void buy_withoutToken_returns401() throws Exception {
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/buy", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- sell -----------------------------------------------------------------------------

    @Test
    void sell_quantityExceedsHeld_returns400AndDoesNotChangeState() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 10L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertEquals(5, positionRepository.findBySimulationId(simulation.getId()).get(0).getQuantity());
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));
    }

    @Test
    void sell_neverBoughtTicker_returns400NotAssetNotFound() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        TradeRequestDTO body = new TradeRequestDTO("ZZZZ", 1L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sell_partial_returns200UpdatesPositionAndCashBalance() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 4L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedAmount").value(720.00))
                .andExpect(jsonPath("$.cashBalance").value(1720.00));

        // costBasisRemoved = 900.00 * 4 / 10 = 360.00; remaining costBasis = 540.00; qty = 6.
        Position position = positionRepository.findBySimulationId(simulation.getId()).get(0);
        assertEquals(6, position.getQuantity());
        assertEquals(0, new BigDecimal("540.00").compareTo(position.getCostBasis()));

        List<Transaction> transactions = transactionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, transactions.size());
        assertEquals(TransactionType.SELL, transactions.get(0).getType());
        assertEquals(0, new BigDecimal("720.00").compareTo(transactions.get(0).getAmount()));
        assertEquals(0, new BigDecimal("4").compareTo(transactions.get(0).getQuantity()));
    }

    @Test
    void sell_partial_withOtherPosition_recalculatesOtherPositionWeightToo() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("900.00"), BigDecimal.ZERO);
        seedPosition(simulation.getId(), msft.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 5L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                // Remaining AAPL value = 180.00 * 5 = 900.00; MSFT value = 180.00 * 5 = 900.00; total = 1800.00.
                .andExpect(jsonPath("$.totalAssetValue").value(1800.00));

        Position reloadedMsft = positionRepository.findBySimulationId(simulation.getId()).stream()
                .filter(p -> p.getAssetId().equals(msft.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.5").compareTo(reloadedMsft.getWeight()));
    }

    @Test
    void sell_full_returns200DeletesPositionRowAndEvictsOrphanedAsset() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 10L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").doesNotExist())
                .andExpect(jsonPath("$.totalAssetValue").value(0));

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(assetCatalogRepository.findByTicker("AAPL").isEmpty());
    }

    @Test
    void sell_full_keepsAssetCatalogWhenOtherSimulationStillHolds() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 10, new BigDecimal("900.00"), BigDecimal.ZERO);

        User otherUser = createUser(u -> u.setEmail("grace@example.com"));
        Simulation otherSimulation = seedSimulation(otherUser.getId(), "Other plan", "USD");
        seedPosition(otherSimulation.getId(), aapl.getId(), 3, new BigDecimal("270.00"), BigDecimal.ZERO);

        TradeRequestDTO body = new TradeRequestDTO("AAPL", 10L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(assetCatalogRepository.findByTicker("AAPL").isPresent());
    }

    @Test
    void sell_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/sell", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sell_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/sell", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        assertEquals(5, positionRepository.findBySimulationId(simulation.getId()).get(0).getQuantity());
    }

    @Test
    void sell_withoutToken_returns401() throws Exception {
        TradeRequestDTO body = new TradeRequestDTO("AAPL", 1L);

        mockMvc.perform(post("/simulations/{id}/sell", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // --- advance --------------------------------------------------------------------------

    @Test
    void advance_ownSimulation_returns200RepricesPositionCreditsDividendAndRecalculatesTotals() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        // seedAssetCatalog only caches January 2024; advancing to February forces a refresh,
        // which this stubs with a new close price and a per-share dividend.
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, new BigDecimal("2.00"), null))));

        MvcResult result = mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].wasTruncated").value(false))
                .andReturn();

        AdvanceMonthResponseDTO response = objectMapper.readValue(readBody(result), AdvanceMonthResponseDTO.class);
        assertEquals(YearMonth.of(2024, 2), response.currentMonth());
        // price = 200.00 (same currency, no conversion); totalAssetValue = 200.00 * 5 = 1000.00.
        assertEquals(0, new BigDecimal("200.00").compareTo(response.positions().get(0).price()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.totalAssetValue()));
        // dividend = 2.00/share * 5 shares = 10.00, credited to cash on top of the 1000.00 seeded.
        assertEquals(0, new BigDecimal("10.00").compareTo(response.positions().get(0).dividendReceived()));
        assertEquals(0, new BigDecimal("1010.00").compareTo(response.cashBalance()));
        assertEquals(0, new BigDecimal("900.00").compareTo(response.positions().get(0).costBasis()));
        assertEquals(0, BigDecimal.ONE.compareTo(response.positions().get(0).weight()));

        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(YearMonth.of(2024, 2), reloaded.getCurrentMonth());
        assertEquals(0, new BigDecimal("1010.00").compareTo(reloaded.getCashBalance()));

        Position reloadedPosition = positionRepository.findBySimulationId(simulation.getId()).get(0);
        assertEquals(0, new BigDecimal("900.00").compareTo(reloadedPosition.getCostBasis()));
        assertEquals(0, new BigDecimal("10.00").compareTo(reloadedPosition.getTotalDividendsReceived()));

        List<Transaction> dividendTransactions = transactionRepository.findBySimulationId(simulation.getId()).stream()
                .filter(t -> t.getType() == TransactionType.DIVIDEND).toList();
        assertEquals(1, dividendTransactions.size());
        assertEquals(0, new BigDecimal("10.00").compareTo(dividendTransactions.get(0).getAmount()));
        assertEquals("AAPL", dividendTransactions.get(0).getTicker());
        assertEquals(YearMonth.of(2024, 2), dividendTransactions.get(0).getMonth());
        assertNull(dividendTransactions.get(0).getQuantity());
    }

    @Test
    void advance_crossCurrency_convertsPriceAndDividendUsingTheSameRate() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "BRL", BigDecimal.ZERO);
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("2.00"), new BigDecimal("2.00"),
                        new BigDecimal("2.00"), new BigDecimal("2.00"), 1_000_000L, new BigDecimal("0.10"), null))));
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(
                List.of(new RawExchangeMonthDataPoint(simulation.getCurrentMonth(),
                        new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"), new BigDecimal("0.20"))));

        MvcResult result = mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // Stored canonical rate is BRL/USD 0.20; USD->BRL is its inverse, 5.00 - the one rate
        // applied to both this position's price and its dividend.
        AdvanceMonthResponseDTO response = objectMapper.readValue(readBody(result), AdvanceMonthResponseDTO.class);
        assertEquals(0, new BigDecimal("10.00").compareTo(response.positions().get(0).price())); // 2.00 * 5.00
        assertEquals(0, new BigDecimal("2.50").compareTo(response.positions().get(0).dividendReceived())); // 0.10 * 5 * 5.00
        assertEquals(0, new BigDecimal("50.00").compareTo(response.totalAssetValue())); // 10.00 * 5 shares
        assertEquals(0, new BigDecimal("2.50").compareTo(response.cashBalance()));
    }

    @Test
    void advance_wouldExceedRealCurrentMonth_returns400AndDoesNotChangeMonth() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        simulation.setCurrentMonth(YearMonth.now());
        simulation = simulationRepository.save(simulation);

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertEquals(YearMonth.now(), simulationRepository.findById(simulation.getId()).orElseThrow().getCurrentMonth());
    }

    @Test
    void advance_someTruncatedSomeNot_flagsPerPositionIndependently() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        seedPosition(simulation.getId(), msft.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        // AAPL's refresh reaches February; MSFT's refresh runs but the upstream feed still only
        // has January (a ticker whose new month genuinely isn't out yet) - truncated stays true
        // for MSFT alone, independent of AAPL's own flag.
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, null, null))));
        when(dataServiceAssetClient.fetchSeries("MSFT")).thenReturn(new RawAssetSeries(
                "MSFT", "Microsoft Corp.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 1), new BigDecimal("180.00"), new BigDecimal("180.00"),
                        new BigDecimal("180.00"), new BigDecimal("180.00"), 1_000_000L, null, null))));

        MvcResult result = mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        AdvanceMonthResponseDTO response = objectMapper.readValue(readBody(result), AdvanceMonthResponseDTO.class);
        var aaplResult = response.positions().stream().filter(p -> p.ticker().equals("AAPL")).findFirst().orElseThrow();
        var msftResult = response.positions().stream().filter(p -> p.ticker().equals("MSFT")).findFirst().orElseThrow();
        assertFalse(aaplResult.wasTruncated());
        assertTrue(msftResult.wasTruncated());
    }

    @Test
    void advance_upstreamRefreshFailureForAlreadyHeldTicker_returns200UsingStaleCachedData() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        // Simulates a network/upstream failure on refresh; AssetCacheService already catches
        // this for a ticker with existing cached data and falls back to serving it stale
        // (January's row, seeded by seedAssetCatalog) instead of failing the whole operation.
        when(dataServiceAssetClient.fetchSeries("AAPL"))
                .thenThrow(new AssetDataServiceException("AAPL", new RuntimeException("upstream unreachable")));

        MvcResult result = mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        AdvanceMonthResponseDTO response = objectMapper.readValue(readBody(result), AdvanceMonthResponseDTO.class);
        assertTrue(response.positions().get(0).wasTruncated());
        assertEquals(0, new BigDecimal("180.00").compareTo(response.positions().get(0).price()));
        assertEquals(0, new BigDecimal("900.00").compareTo(response.totalAssetValue()));
        assertEquals(YearMonth.of(2024, 2), response.currentMonth());
    }

    @Test
    void advance_midLoopFailure_rollsBackEntireAdvance() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        AssetCatalog msft = seedAssetCatalog("MSFT", "Microsoft Corp.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        seedPosition(simulation.getId(), msft.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, null, null))));
        // An unexpected failure type (not one AssetCacheService's refresh catch clause handles)
        // simulates an unrecoverable mid-loop error on the second position, to prove the whole
        // advance - including AAPL's already-processed reprice above - is rolled back, not
        // partially applied.
        when(dataServiceAssetClient.fetchSeries("MSFT")).thenThrow(new RuntimeException("simulated corruption"));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(YearMonth.of(2024, 1), reloaded.getCurrentMonth());
        assertEquals(0, new BigDecimal("1000.00").compareTo(reloaded.getCashBalance()));
        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void advance_noPositions_returns200WithZeroTotalAssetValue() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssetValue").value(0))
                .andExpect(jsonPath("$.positions.length()").value(0));
    }

    @Test
    void advance_nonexistentSimulation_returns404() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);

        mockMvc.perform(post("/simulations/{id}/advance", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void advance_anotherUsersSimulation_returns404() throws Exception {
        User owner = createUser(u -> {
        });
        User other = createUser(u -> u.setEmail("grace@example.com"));
        String otherToken = tokenService.generateToken(other);
        Simulation simulation = seedSimulation(owner.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertEquals(YearMonth.of(2024, 1), simulationRepository.findById(simulation.getId()).orElseThrow().getCurrentMonth());
    }

    @Test
    void advance_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/simulations/{id}/advance", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void advance_ownSimulation_createsSnapshotMatchingPostAdvanceState() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, new BigDecimal("2.00"), null))));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // price = 200.00 * 5 shares = 1000.00; dividend = 2.00/share * 5 = 10.00 credited to
        // the 1000.00 seeded cash, matching advance_ownSimulation_...RecalculatesTotals above.
        Snapshot snapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1010.00").compareTo(snapshot.getCashBalance()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(snapshot.getTotalAssetValue()));

        List<SnapshotPosition> snapshotPositions = snapshotPositionRepository.findBySnapshotId(snapshot.getId());
        assertEquals(1, snapshotPositions.size());
        assertEquals("AAPL", snapshotPositions.get(0).getTicker());
        assertEquals(5, snapshotPositions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("900.00").compareTo(snapshotPositions.get(0).getCostBasis()));
    }

    @Test
    void advance_snapshotStepFailure_rollsBackEntireAdvanceAndLeavesExistingSnapshotUntouched() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        // A snapshot from a prior, already-completed advance - this must survive a failed
        // advance untouched, proving the failure doesn't partially overwrite it either.
        Snapshot existingSnapshot = seedSnapshot(simulation.getId(), new BigDecimal("500.00"), new BigDecimal("300.00"));
        // Price change, a dividend, and a clean 2-for-1 forward split all in the same month,
        // so the failed advance below has price/dividend/split changes to roll back, not just
        // a reprice.
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                        new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, new BigDecimal("2.00"), new BigDecimal("2")))));
        // Simulates an unrecoverable failure in the snapshot step itself, after reprice,
        // dividend, and split handling have all already run - proves the shared
        // @Transactional boundary rolls back everything, not just the snapshot write.
        doThrow(new RuntimeException("simulated snapshot failure")).when(snapshotRepository).save(any(Snapshot.class));

        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(YearMonth.of(2024, 1), reloaded.getCurrentMonth());
        assertEquals(0, new BigDecimal("1000.00").compareTo(reloaded.getCashBalance()));
        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());

        // The 2-for-1 split (5 -> 10 shares) and the dividend credit must not have persisted
        // either - the position is exactly as it was before the advance was attempted.
        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(5, positions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("900.00").compareTo(positions.get(0).getCostBasis()));
        assertEquals(0, BigDecimal.ZERO.compareTo(positions.get(0).getTotalDividendsReceived()));

        Snapshot reloadedSnapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseThrow();
        assertEquals(existingSnapshot.getId(), reloadedSnapshot.getId());
        assertEquals(0, new BigDecimal("500.00").compareTo(reloadedSnapshot.getCashBalance()));
        assertEquals(0, new BigDecimal("300.00").compareTo(reloadedSnapshot.getTotalAssetValue()));
    }

    @Test
    void advance_sequentialAdvances_onlyLatestSnapshotValidForReset() throws Exception {
        User user = createUser(u -> {
        });
        String token = tokenService.generateToken(user);
        Simulation simulation = seedSimulation(user.getId(), "Retirement plan", "USD", new BigDecimal("1000.00"));
        AssetCatalog aapl = seedAssetCatalog("AAPL", "Apple Inc.");
        seedPosition(simulation.getId(), aapl.getId(), 5, new BigDecimal("900.00"), BigDecimal.ZERO);
        // Both months are returned by the same stub - the first advance's refresh (month
        // 1 -> 2) caches both rows, so the second advance (month 2 -> 3) resolves March
        // from cache without needing a second fetchSeries stub.
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2000, 1, 1),
                List.of(
                        new RawAssetMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("200.00"), new BigDecimal("200.00"),
                                new BigDecimal("200.00"), new BigDecimal("200.00"), 1_000_000L, null, null),
                        new RawAssetMonthDataPoint(YearMonth.of(2024, 3), new BigDecimal("220.00"), new BigDecimal("220.00"),
                                new BigDecimal("220.00"), new BigDecimal("220.00"), 1_000_000L, new BigDecimal("2.00"), null))));

        // Advance 1: month 1 -> 2. Snapshot now reflects month 2's opening state
        // (cash 1000.00, totalAssetValue 1000.00) - about to be overwritten below.
        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Advance 2: month 2 -> 3. Dividend (2.00/share * 5 = 10.00) makes month 3's cash
        // distinguishable from both month 1's and month 2's snapshot cash (both 1000.00).
        mockMvc.perform(post("/simulations/{id}/advance", simulation.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long snapshotCountForSimulation = snapshotRepository.findAll().stream()
                .filter(s -> s.getSimulationId().equals(simulation.getId()))
                .count();
        assertEquals(1, snapshotCountForSimulation);

        Snapshot snapshot = snapshotRepository.findBySimulationId(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1010.00").compareTo(snapshot.getCashBalance()));
        assertEquals(0, new BigDecimal("1100.00").compareTo(snapshot.getTotalAssetValue()));

        // A further mutation in month 3, after the snapshot was taken, so reset has
        // something to actually undo.
        CashMovementRequestDTO deposit = new CashMovementRequestDTO(new BigDecimal("500.00"), false);
        mockMvc.perform(post("/simulations/{id}/deposits", simulation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isOk());
        assertEquals(0, new BigDecimal("1510.00").compareTo(
                simulationRepository.findById(simulation.getId()).orElseThrow().getCashBalance()));

        mockMvc.perform(post("/simulations/{id}/reset", simulation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Restored to month 3's snapshot (1010.00), not the post-deposit value, and not
        // month 1's or month 2's snapshot cash (both 1000.00).
        Simulation reloaded = simulationRepository.findById(simulation.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1010.00").compareTo(reloaded.getCashBalance()));
        assertEquals(0, new BigDecimal("1100.00").compareTo(reloaded.getTotalAssetValue()));

        List<Position> positions = positionRepository.findBySimulationId(simulation.getId());
        assertEquals(1, positions.size());
        assertEquals(5, positions.get(0).getQuantity());
        assertEquals(0, new BigDecimal("900.00").compareTo(positions.get(0).getCostBasis()));
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
        seedTransaction(simulation.getId(), TransactionType.BUY, simulation.getCurrentMonth(), "MSFT", "Microsoft Corp.", BigDecimal.valueOf(4));

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
