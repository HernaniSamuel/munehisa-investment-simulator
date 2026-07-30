package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class SnapshotRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SnapshotRepository snapshotRepository;

    private Simulation persistSimulation() {
        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail("ada+" + UUID.randomUUID() + "@example.com");
        user.setPassword("hashed-password");
        User savedUser = userRepository.saveAndFlush(user);

        Simulation simulation = new Simulation();
        simulation.setUserId(savedUser.getId());
        simulation.setName("Retirement plan");
        simulation.setBaseCurrency("USD");
        simulation.setStartMonth(YearMonth.of(2024, 1));
        simulation.setCurrentMonth(YearMonth.of(2024, 6));
        simulation.setCashBalance(new BigDecimal("1000.00"));
        simulation.setTotalAssetValue(new BigDecimal("500.00"));
        return simulationRepository.saveAndFlush(simulation);
    }

    private Snapshot persist(UUID simulationId) {
        Snapshot snapshot = new Snapshot();
        snapshot.setSimulationId(simulationId);
        snapshot.setCashBalance(new BigDecimal("1000.00"));
        snapshot.setTotalAssetValue(new BigDecimal("500.00"));
        return snapshotRepository.saveAndFlush(snapshot);
    }

    @Test
    void saveAndLoad_roundTrip() {
        Simulation simulation = persistSimulation();

        Snapshot saved = persist(simulation.getId());

        Snapshot loaded = snapshotRepository.findById(saved.getId()).orElseThrow();
        assertEquals(simulation.getId(), loaded.getSimulationId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(loaded.getCashBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(loaded.getTotalAssetValue()));
    }

    @Test
    void deletingSimulation_cascadesToSnapshot() {
        Simulation simulation = persistSimulation();
        persist(simulation.getId());

        simulationRepository.delete(simulation);
        simulationRepository.flush();

        assertTrue(snapshotRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void uniqueConstraint_rejectsSecondSnapshotForSameSimulation() {
        Simulation simulation = persistSimulation();
        persist(simulation.getId());

        assertThrows(DataIntegrityViolationException.class, () ->
                persist(simulation.getId()));
    }

    @Test
    void foreignKeyConstraint_rejectsSnapshotForUnknownSimulation() {
        assertThrows(DataIntegrityViolationException.class, () ->
                persist(UUID.randomUUID()));
    }
}
