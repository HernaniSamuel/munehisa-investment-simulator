package com.munehisa.backend.service;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.AssetLookupResultDTO;
import com.munehisa.backend.dto.AssetSearchResponseDTO;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.ExchangeRateLookupResultDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.AssetPredatesStartDateException;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.exceptions.SnapshotNotFoundException;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.PositionRepository;
import com.munehisa.backend.repository.SimulationRepository;
import com.munehisa.backend.repository.SnapshotPositionRepository;
import com.munehisa.backend.repository.SnapshotRepository;
import com.munehisa.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mirrors InflationDeflationServiceTest's fixed-clock pattern to pin down the exact
 * boundary of the future-start-month guard, which SimulationControllerIntegrationTest
 * can only approximate against the real system clock.
 */
@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private InflationDeflationService inflationDeflationService;

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private SnapshotPositionRepository snapshotPositionRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private AssetCatalogRepository assetCatalogRepository;

    @Mock
    private AssetCacheService assetCacheService;

    @Mock
    private ExchangeRateCacheService exchangeRateCacheService;

    private SimulationService buildService(Clock clock) {
        return new SimulationService(simulationRepository, transactionRepository, inflationDeflationService, clock,
                snapshotRepository, snapshotPositionRepository, positionRepository, assetCatalogRepository, assetCacheService,
                exchangeRateCacheService);
    }

    private static Clock fixedClockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void create_startMonthEqualsCurrentMonth_succeeds() {
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SimulationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        User user = user();
        SimulationResponseDTO response = service.create(
                new CreateSimulationRequestDTO("Retirement plan", "BRL", currentMonth), user);

        assertEquals(currentMonth, response.startMonth());
        assertEquals(currentMonth, response.currentMonth());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.cashBalance()));

        ArgumentCaptor<Simulation> captor = ArgumentCaptor.forClass(Simulation.class);
        verify(simulationRepository).save(captor.capture());
        assertEquals(user.getId(), captor.getValue().getUserId());
    }

    @Test
    void create_startMonthOneMonthAfterCurrentMonth_throwsFutureSimulationStartMonthException() {
        SimulationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        YearMonth startMonth = YearMonth.of(2024, 8);

        assertThrows(FutureSimulationStartMonthException.class, () ->
                service.create(new CreateSimulationRequestDTO("Retirement plan", "BRL", startMonth), user()));

        verify(simulationRepository, never()).save(any());
    }

    @Test
    void get_ownedSimulation_returnsMappedResponse() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId()))
                .thenReturn(Optional.of(simulation));

        SimulationResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).get(simulation.getId(), user);

        assertEquals(simulation.getId(), response.id());
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.totalPatrimony()));
    }

    @Test
    void get_notOwnedOrMissingSimulation_throwsSimulationNotFoundException() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).get(id, user));
    }

    @Test
    void rename_notOwnedOrMissingSimulation_throwsSimulationNotFoundExceptionAndDoesNotSave() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                        .rename(id, new RenameSimulationRequestDTO("New name"), user));

        verify(simulationRepository, never()).save(any());
    }

    @Test
    void delete_notOwnedOrMissingSimulation_throwsSimulationNotFoundExceptionAndDoesNotDelete() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).delete(id, user));

        verify(simulationRepository, never()).delete(any());
    }

    @Test
    void list_returnsOnlyMappedSimulationsForGivenUser() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByUserId(user.getId())).thenReturn(List.of(simulation));

        List<SimulationResponseDTO> result = buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).list(user);

        assertEquals(1, result.size());
        assertEquals(simulation.getId(), result.get(0).id());
    }

    // --- searchAsset -----------------------------------------------------------------------

    @Test
    void searchAsset_sameCurrency_returnsSeriesAndUnconvertedCashBalance() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));

        AssetLookupResultDTO lookup = new AssetLookupResultDTO(
                "AAPL", "Apple Inc.", "BRL", simulation.getCurrentMonth(), simulation.getCurrentMonth(), false, List.of());
        when(assetCacheService.getAssetSeries("AAPL", simulation.getCurrentMonth())).thenReturn(lookup);
        when(exchangeRateCacheService.getExchangeRate("BRL", "BRL", simulation.getCurrentMonth()))
                .thenReturn(exchangeRate("BRL", "BRL", simulation.getCurrentMonth(), BigDecimal.ONE));

        AssetSearchResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .searchAsset(simulation.getId(), "AAPL", user);

        assertEquals("AAPL", response.ticker());
        assertEquals("BRL", response.currency());
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.cashBalance().amount()));
        assertEquals("BRL", response.cashBalance().currency());
        assertFalse(response.cashBalance().wasConverted());
    }

    @Test
    void searchAsset_crossCurrency_returnsConvertedCashBalance() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));

        AssetLookupResultDTO lookup = new AssetLookupResultDTO(
                "AAPL", "Apple Inc.", "USD", simulation.getCurrentMonth(), simulation.getCurrentMonth(), false, List.of());
        when(assetCacheService.getAssetSeries("AAPL", simulation.getCurrentMonth())).thenReturn(lookup);
        when(exchangeRateCacheService.getExchangeRate("BRL", "USD", simulation.getCurrentMonth()))
                .thenReturn(exchangeRate("BRL", "USD", simulation.getCurrentMonth(), new BigDecimal("0.20")));

        AssetSearchResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .searchAsset(simulation.getId(), "AAPL", user);

        assertEquals("USD", response.currency());
        assertEquals(0, new BigDecimal("200.00").compareTo(response.cashBalance().amount()));
        assertEquals("USD", response.cashBalance().currency());
        assertTrue(response.cashBalance().wasConverted());
    }

    @Test
    void searchAsset_unknownTicker_propagatesAssetNotFoundException() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(assetCacheService.getAssetSeries("ZZZZ", simulation.getCurrentMonth()))
                .thenThrow(new AssetNotFoundException("ZZZZ", new RuntimeException("404")));

        assertThrows(AssetNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).searchAsset(simulation.getId(), "ZZZZ", user));

        verifyNoInteractions(exchangeRateCacheService);
    }

    @Test
    void searchAsset_tickerPredatesStartDate_propagatesAssetPredatesStartDateException() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(assetCacheService.getAssetSeries("TSLA", simulation.getCurrentMonth()))
                .thenThrow(new AssetPredatesStartDateException("TSLA", simulation.getCurrentMonth(), LocalDate.of(2010, 6, 29)));

        assertThrows(AssetPredatesStartDateException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).searchAsset(simulation.getId(), "TSLA", user));

        verifyNoInteractions(exchangeRateCacheService);
    }

    @Test
    void searchAsset_notOwnedOrMissingSimulation_throwsSimulationNotFoundException() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).searchAsset(id, "AAPL", user));

        verifyNoInteractions(assetCacheService, exchangeRateCacheService);
    }

    private ExchangeRateLookupResultDTO exchangeRate(String from, String to, YearMonth month, BigDecimal rate) {
        return new ExchangeRateLookupResultDTO(from, to, month, month, rate, rate, rate, rate, true, true);
    }

    // --- deposit --------------------------------------------------------------------------

    @Test
    void deposit_todaysMoneyFalse_appliesRawAmountAndRecordsTransaction() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashMovementResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .deposit(simulation.getId(), new CashMovementRequestDTO(new BigDecimal("200.00"), false), user);

        assertEquals(0, new BigDecimal("200.00").compareTo(response.appliedAmount()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(response.cashBalance()));
        assertEquals(0, new BigDecimal("1700.00").compareTo(response.totalPatrimony()));
        assertNull(response.deflation());
        verifyNoInteractions(inflationDeflationService);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(simulation.getId(), saved.getSimulationId());
        assertEquals(TransactionType.DEPOSIT, saved.getType());
        assertEquals(simulation.getCurrentMonth(), saved.getMonth());
        assertEquals(0, new BigDecimal("200.00").compareTo(saved.getAmount()));
    }

    @Test
    void deposit_todaysMoneyTrue_appliesDeflatedAmountAndPropagatesFallbackFlags() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InflationDeflationResultDTO deflationResult = deflationResult(
                new BigDecimal("300.00"), new BigDecimal("250.00"), simulation.getCurrentMonth());
        when(inflationDeflationService.deflate(new BigDecimal("300.00"), "BRL", simulation.getCurrentMonth()))
                .thenReturn(deflationResult);

        CashMovementResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .deposit(simulation.getId(), new CashMovementRequestDTO(new BigDecimal("300.00"), true), user);

        assertEquals(0, new BigDecimal("250.00").compareTo(response.appliedAmount()));
        assertEquals(0, new BigDecimal("1250.00").compareTo(response.cashBalance()));
        assertNotNull(response.deflation());
        assertTrue(response.deflation().targetMonthLookup().oldestAvailable());
        assertFalse(response.deflation().targetMonthLookup().newestAvailable());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("250.00").compareTo(captor.getValue().getAmount()));
    }

    @Test
    void deposit_notOwnedOrMissingSimulation_throwsSimulationNotFoundExceptionAndDoesNotSave() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                        .deposit(id, new CashMovementRequestDTO(new BigDecimal("100.00"), false), user));

        verify(simulationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- withdraw -------------------------------------------------------------------------

    @Test
    void withdraw_todaysMoneyFalse_appliesRawAmountAndRecordsTransaction() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashMovementResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .withdraw(simulation.getId(), new CashMovementRequestDTO(new BigDecimal("400.00"), false), user);

        assertEquals(0, new BigDecimal("400.00").compareTo(response.appliedAmount()));
        assertEquals(0, new BigDecimal("600.00").compareTo(response.cashBalance()));
        assertNull(response.deflation());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.WITHDRAWAL, captor.getValue().getType());
        assertEquals(0, new BigDecimal("400.00").compareTo(captor.getValue().getAmount()));
    }

    @Test
    void withdraw_amountExceedsCashBalance_throwsInsufficientCashBalanceExceptionAndDoesNotSave() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));

        assertThrows(InsufficientCashBalanceException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                        .withdraw(simulation.getId(), new CashMovementRequestDTO(new BigDecimal("1500.00"), false), user));

        verify(simulationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_deflatedAmountExceedsCashBalance_throwsInsufficientCashBalanceExceptionAndDoesNotSave() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(inflationDeflationService.deflate(new BigDecimal("900.00"), "BRL", simulation.getCurrentMonth()))
                .thenReturn(deflationResult(new BigDecimal("900.00"), new BigDecimal("1200.00"), simulation.getCurrentMonth()));

        assertThrows(InsufficientCashBalanceException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                        .withdraw(simulation.getId(), new CashMovementRequestDTO(new BigDecimal("900.00"), true), user));

        verify(simulationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_notOwnedOrMissingSimulation_throwsSimulationNotFoundExceptionAndDoesNotSave() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                        .withdraw(id, new CashMovementRequestDTO(new BigDecimal("100.00"), false), user));

        verify(simulationRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- createSnapshot ---------------------------------------------------------------------

    @Test
    void createSnapshot_noExistingSnapshot_upsertsSnapshotAndPositions() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(snapshotRepository.findBySimulationId(simulation.getId())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(invocation -> {
            Snapshot saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(snapshotPositionRepository.findBySnapshotId(any())).thenReturn(List.of());

        UUID assetId = UUID.randomUUID();
        Position position = position(simulation.getId(), assetId, 10, "1.0", "1500.00", "20.00");
        when(positionRepository.findBySimulationId(simulation.getId())).thenReturn(List.of(position));
        when(assetCatalogRepository.findById(assetId)).thenReturn(Optional.of(assetCatalog(assetId, "AAPL", "Apple Inc.")));

        buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).createSnapshot(simulation.getId(), user);

        ArgumentCaptor<Snapshot> snapshotCaptor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertEquals(simulation.getId(), snapshotCaptor.getValue().getSimulationId());
        assertEquals(0, simulation.getCashBalance().compareTo(snapshotCaptor.getValue().getCashBalance()));
        assertEquals(0, simulation.getTotalAssetValue().compareTo(snapshotCaptor.getValue().getTotalAssetValue()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SnapshotPosition>> positionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotPositionRepository).saveAll(positionsCaptor.capture());
        List<SnapshotPosition> saved = positionsCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("AAPL", saved.get(0).getTicker());
        assertEquals("Apple Inc.", saved.get(0).getAssetName());
        assertEquals(10, saved.get(0).getQuantity());
        assertEquals(0, new BigDecimal("1500.00").compareTo(saved.get(0).getCostBasis()));
    }

    @Test
    void createSnapshot_existingSnapshot_overwritesInPlaceAndReplacesOldPositions() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));

        Snapshot existingSnapshot = new Snapshot();
        existingSnapshot.setId(UUID.randomUUID());
        existingSnapshot.setSimulationId(simulation.getId());
        existingSnapshot.setCashBalance(BigDecimal.ZERO);
        existingSnapshot.setTotalAssetValue(BigDecimal.ZERO);
        when(snapshotRepository.findBySimulationId(simulation.getId())).thenReturn(Optional.of(existingSnapshot));
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SnapshotPosition staleSnapshotPosition = new SnapshotPosition();
        staleSnapshotPosition.setId(UUID.randomUUID());
        staleSnapshotPosition.setSnapshotId(existingSnapshot.getId());
        when(snapshotPositionRepository.findBySnapshotId(existingSnapshot.getId())).thenReturn(List.of(staleSnapshotPosition));
        when(positionRepository.findBySimulationId(simulation.getId())).thenReturn(List.of());

        buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).createSnapshot(simulation.getId(), user);

        verify(snapshotPositionRepository).deleteAll(List.of(staleSnapshotPosition));
        ArgumentCaptor<Snapshot> snapshotCaptor = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertEquals(existingSnapshot.getId(), snapshotCaptor.getValue().getId());
        assertEquals(0, simulation.getCashBalance().compareTo(snapshotCaptor.getValue().getCashBalance()));
    }

    @Test
    void createSnapshot_notOwnedOrMissingSimulation_throwsSimulationNotFoundExceptionAndDoesNotTouchSnapshot() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).createSnapshot(id, user));

        verifyNoInteractions(snapshotRepository, snapshotPositionRepository, positionRepository);
    }

    // --- resetToSnapshot --------------------------------------------------------------------

    @Test
    void resetToSnapshot_noSnapshot_throwsSnapshotNotFoundExceptionAndDoesNotChangeAnything() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(snapshotRepository.findBySimulationId(simulation.getId())).thenReturn(Optional.empty());

        assertThrows(SnapshotNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).resetToSnapshot(simulation.getId(), user));

        verify(simulationRepository, never()).save(any());
        verify(positionRepository, never()).deleteAll(any());
        verifyNoInteractions(assetCacheService);
        verify(transactionRepository, never()).deleteAll(any());
    }

    @Test
    void resetToSnapshot_notOwnedOrMissingSimulation_throwsSimulationNotFoundException() {
        User user = user();
        UUID id = UUID.randomUUID();
        when(simulationRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThrows(SimulationNotFoundException.class, () ->
                buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).resetToSnapshot(id, user));

        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void resetToSnapshot_happyPath_restoresCashRecreatesPositionsAndDeletesMonthNonDividendTransactions() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Snapshot snapshot = new Snapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setSimulationId(simulation.getId());
        snapshot.setCashBalance(new BigDecimal("1200.00"));
        snapshot.setTotalAssetValue(new BigDecimal("300.00"));
        when(snapshotRepository.findBySimulationId(simulation.getId())).thenReturn(Optional.of(snapshot));

        SnapshotPosition snapshotPosition = new SnapshotPosition();
        snapshotPosition.setSnapshotId(snapshot.getId());
        snapshotPosition.setTicker("AAPL");
        snapshotPosition.setAssetName("Apple Inc.");
        snapshotPosition.setQuantity(5);
        snapshotPosition.setWeight(new BigDecimal("1.0"));
        snapshotPosition.setCostBasis(new BigDecimal("750.00"));
        snapshotPosition.setTotalDividendsReceived(BigDecimal.ZERO);
        when(snapshotPositionRepository.findBySnapshotId(snapshot.getId())).thenReturn(List.of(snapshotPosition));

        // Bought after the snapshot was taken - the revert must wipe it out and evict its asset.
        UUID staleAssetId = UUID.randomUUID();
        Position stalePosition = position(simulation.getId(), staleAssetId, 3, "1.0", "900.00", "0.00");
        when(positionRepository.findBySimulationId(simulation.getId())).thenReturn(List.of(stalePosition));

        UUID aaplAssetId = UUID.randomUUID();
        when(assetCacheService.getAssetSeries("AAPL", simulation.getCurrentMonth())).thenReturn(new AssetLookupResultDTO(
                "AAPL", "Apple Inc.", "USD", simulation.getCurrentMonth(), simulation.getCurrentMonth(), false, List.of()));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(assetCatalog(aaplAssetId, "AAPL", "Apple Inc.")));

        Transaction depositThisMonth = transaction(simulation.getId(), TransactionType.DEPOSIT, simulation.getCurrentMonth());
        Transaction dividendThisMonth = transaction(simulation.getId(), TransactionType.DIVIDEND, simulation.getCurrentMonth());
        Transaction buyLastMonth = transaction(simulation.getId(), TransactionType.BUY, simulation.getCurrentMonth().minusMonths(1));
        when(transactionRepository.findBySimulationId(simulation.getId()))
                .thenReturn(List.of(depositThisMonth, dividendThisMonth, buyLastMonth));

        SimulationResponseDTO response = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)))
                .resetToSnapshot(simulation.getId(), user);

        assertEquals(0, new BigDecimal("1200.00").compareTo(response.cashBalance()));
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.totalPatrimony()));

        verify(positionRepository).deleteAll(List.of(stalePosition));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Position>> newPositionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(positionRepository).saveAll(newPositionsCaptor.capture());
        assertEquals(1, newPositionsCaptor.getValue().size());
        assertEquals(aaplAssetId, newPositionsCaptor.getValue().get(0).getAssetId());
        assertEquals(5, newPositionsCaptor.getValue().get(0).getQuantity());

        verify(assetCacheService).evictIfOrphaned(staleAssetId);
        verify(assetCacheService, never()).evictIfOrphaned(aaplAssetId);

        verify(transactionRepository).deleteAll(List.of(depositThisMonth));
    }

    @Test
    void resetToSnapshot_assetPresentBeforeAndAfterReset_doesNotEvictIt() {
        User user = user();
        Simulation simulation = simulation(user.getId());
        when(simulationRepository.findByIdAndUserId(simulation.getId(), user.getId())).thenReturn(Optional.of(simulation));
        when(simulationRepository.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Snapshot snapshot = new Snapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setSimulationId(simulation.getId());
        snapshot.setCashBalance(simulation.getCashBalance());
        snapshot.setTotalAssetValue(simulation.getTotalAssetValue());
        when(snapshotRepository.findBySimulationId(simulation.getId())).thenReturn(Optional.of(snapshot));

        UUID assetId = UUID.randomUUID();
        SnapshotPosition snapshotPosition = new SnapshotPosition();
        snapshotPosition.setSnapshotId(snapshot.getId());
        snapshotPosition.setTicker("AAPL");
        snapshotPosition.setAssetName("Apple Inc.");
        snapshotPosition.setQuantity(5);
        snapshotPosition.setWeight(new BigDecimal("1.0"));
        snapshotPosition.setCostBasis(new BigDecimal("750.00"));
        snapshotPosition.setTotalDividendsReceived(BigDecimal.ZERO);
        when(snapshotPositionRepository.findBySnapshotId(snapshot.getId())).thenReturn(List.of(snapshotPosition));

        Position existingPosition = position(simulation.getId(), assetId, 5, "1.0", "750.00", "0.00");
        when(positionRepository.findBySimulationId(simulation.getId())).thenReturn(List.of(existingPosition));

        when(assetCacheService.getAssetSeries("AAPL", simulation.getCurrentMonth())).thenReturn(new AssetLookupResultDTO(
                "AAPL", "Apple Inc.", "USD", simulation.getCurrentMonth(), simulation.getCurrentMonth(), false, List.of()));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(assetCatalog(assetId, "AAPL", "Apple Inc.")));
        when(transactionRepository.findBySimulationId(simulation.getId())).thenReturn(List.of());

        buildService(fixedClockOn(LocalDate.of(2024, 7, 15))).resetToSnapshot(simulation.getId(), user);

        verify(assetCacheService, never()).evictIfOrphaned(any());
    }

    private Position position(UUID simulationId, UUID assetId, long quantity, String weight, String costBasis, String totalDividendsReceived) {
        Position position = new Position();
        position.setId(UUID.randomUUID());
        position.setSimulationId(simulationId);
        position.setAssetId(assetId);
        position.setQuantity(quantity);
        position.setWeight(new BigDecimal(weight));
        position.setCostBasis(new BigDecimal(costBasis));
        position.setTotalDividendsReceived(new BigDecimal(totalDividendsReceived));
        return position;
    }

    private AssetCatalog assetCatalog(UUID id, String ticker, String name) {
        AssetCatalog catalog = new AssetCatalog();
        catalog.setId(id);
        catalog.setTicker(ticker);
        catalog.setName(name);
        catalog.setBaseCurrency("USD");
        catalog.setStartDate(LocalDate.of(2000, 1, 1));
        return catalog;
    }

    private Transaction transaction(UUID simulationId, TransactionType type, YearMonth month) {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setSimulationId(simulationId);
        transaction.setType(type);
        transaction.setMonth(month);
        transaction.setAmount(BigDecimal.TEN);
        if (type == TransactionType.BUY || type == TransactionType.SELL || type == TransactionType.DIVIDEND) {
            transaction.setTicker("AAPL");
            transaction.setAssetName("Apple Inc.");
        }
        if (type == TransactionType.BUY || type == TransactionType.SELL) {
            transaction.setQuantity(1L);
        }
        return transaction;
    }

    private InflationDeflationResultDTO deflationResult(BigDecimal originalValue, BigDecimal deflatedValue, YearMonth targetMonth) {
        InflationLookupResultDTO currentMonthLookup = new InflationLookupResultDTO(
                InflationCurrency.BRL, YearMonth.of(2024, 7), YearMonth.of(2024, 7), new BigDecimal("200"), false, true);
        InflationLookupResultDTO targetMonthLookup = new InflationLookupResultDTO(
                InflationCurrency.BRL, targetMonth, targetMonth, new BigDecimal("100"), true, false);
        return new InflationDeflationResultDTO(originalValue, deflatedValue, InflationCurrency.BRL, currentMonthLookup, targetMonthLookup);
    }

    private Simulation simulation(UUID userId) {
        Simulation simulation = new Simulation();
        simulation.setId(UUID.randomUUID());
        simulation.setUserId(userId);
        simulation.setName("Retirement plan");
        simulation.setBaseCurrency("BRL");
        simulation.setStartMonth(YearMonth.of(2024, 1));
        simulation.setCurrentMonth(YearMonth.of(2024, 6));
        simulation.setCashBalance(new BigDecimal("1000.00"));
        simulation.setTotalAssetValue(new BigDecimal("500.00"));
        return simulation;
    }
}
