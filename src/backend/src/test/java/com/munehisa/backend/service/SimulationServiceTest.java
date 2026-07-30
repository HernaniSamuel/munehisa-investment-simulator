package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CashMovementRequestDTO;
import com.munehisa.backend.dto.CashMovementResponseDTO;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.InsufficientCashBalanceException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.repository.SimulationRepository;
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

    private SimulationService buildService(Clock clock) {
        return new SimulationService(simulationRepository, transactionRepository, inflationDeflationService, clock);
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
