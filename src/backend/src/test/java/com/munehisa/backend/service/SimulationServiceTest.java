package com.munehisa.backend.service;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.dto.CreateSimulationRequestDTO;
import com.munehisa.backend.dto.RenameSimulationRequestDTO;
import com.munehisa.backend.dto.SimulationResponseDTO;
import com.munehisa.backend.exceptions.FutureSimulationStartMonthException;
import com.munehisa.backend.exceptions.SimulationNotFoundException;
import com.munehisa.backend.repository.SimulationRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private SimulationService buildService(Clock clock) {
        return new SimulationService(simulationRepository, clock);
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
