package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Snapshot;
import com.munehisa.backend.domain.simulation.SnapshotPosition;
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
class SnapshotPositionRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SnapshotRepository snapshotRepository;

    @Autowired
    private SnapshotPositionRepository snapshotPositionRepository;

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

    private Snapshot persistSnapshot(UUID simulationId) {
        Snapshot snapshot = new Snapshot();
        snapshot.setSimulationId(simulationId);
        snapshot.setCashBalance(new BigDecimal("1000.00"));
        snapshot.setTotalAssetValue(new BigDecimal("500.00"));
        return snapshotRepository.saveAndFlush(snapshot);
    }

    private SnapshotPosition persist(UUID snapshotId) {
        SnapshotPosition position = new SnapshotPosition();
        position.setSnapshotId(snapshotId);
        position.setTicker("AAPL");
        position.setAssetName("Apple Inc.");
        position.setQuantity(10);
        position.setWeight(new BigDecimal("0.25"));
        position.setCostBasis(new BigDecimal("1800.00"));
        position.setTotalDividendsReceived(new BigDecimal("12.50"));
        return snapshotPositionRepository.saveAndFlush(position);
    }

    @Test
    void saveAndLoad_roundTrip() {
        Simulation simulation = persistSimulation();
        Snapshot snapshot = persistSnapshot(simulation.getId());

        SnapshotPosition saved = persist(snapshot.getId());

        SnapshotPosition loaded = snapshotPositionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(snapshot.getId(), loaded.getSnapshotId());
        assertEquals("AAPL", loaded.getTicker());
        assertEquals("Apple Inc.", loaded.getAssetName());
        assertEquals(10, loaded.getQuantity());
        assertEquals(0, new BigDecimal("0.25").compareTo(loaded.getWeight()));
        assertEquals(0, new BigDecimal("1800.00").compareTo(loaded.getCostBasis()));
        assertEquals(0, new BigDecimal("12.50").compareTo(loaded.getTotalDividendsReceived()));
    }

    @Test
    void deletingSimulation_cascadesToSnapshotAndSnapshotPositions() {
        Simulation simulation = persistSimulation();
        Snapshot snapshot = persistSnapshot(simulation.getId());
        persist(snapshot.getId());

        simulationRepository.delete(simulation);
        simulationRepository.flush();

        assertTrue(snapshotRepository.findBySimulationId(simulation.getId()).isEmpty());
        assertTrue(snapshotPositionRepository.findBySnapshotId(snapshot.getId()).isEmpty());
    }

    @Test
    void foreignKeyConstraint_rejectsSnapshotPositionForUnknownSnapshot() {
        assertThrows(DataIntegrityViolationException.class, () ->
                persist(UUID.randomUUID()));
    }
}
