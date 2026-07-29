package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.simulation.Position;
import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.user.User;
import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class PositionRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private AssetCatalogRepository assetCatalogRepository;

    @Autowired
    private PositionRepository positionRepository;

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

    private AssetCatalog persistAssetCatalog(String ticker) {
        AssetCatalog catalog = new AssetCatalog();
        catalog.setTicker(ticker);
        catalog.setName(ticker + " Inc.");
        catalog.setBaseCurrency("USD");
        catalog.setStartDate(LocalDate.of(2020, 1, 1));
        return assetCatalogRepository.saveAndFlush(catalog);
    }

    private Position persist(UUID simulationId, UUID assetId) {
        Position position = new Position();
        position.setSimulationId(simulationId);
        position.setAssetId(assetId);
        position.setQuantity(10);
        position.setWeight(new BigDecimal("0.25"));
        position.setCostBasis(new BigDecimal("1800.00"));
        position.setTotalDividendsReceived(new BigDecimal("12.50"));
        return positionRepository.saveAndFlush(position);
    }

    @Test
    void saveAndLoad_roundTrip() {
        Simulation simulation = persistSimulation();
        AssetCatalog catalog = persistAssetCatalog("AAPL");

        Position saved = persist(simulation.getId(), catalog.getId());

        Optional<Position> found = positionRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        Position loaded = found.get();
        assertEquals(simulation.getId(), loaded.getSimulationId());
        assertEquals(catalog.getId(), loaded.getAssetId());
        assertEquals(10, loaded.getQuantity());
        assertEquals(0, new BigDecimal("0.25").compareTo(loaded.getWeight()));
        assertEquals(0, new BigDecimal("1800.00").compareTo(loaded.getCostBasis()));
        assertEquals(0, new BigDecimal("12.50").compareTo(loaded.getTotalDividendsReceived()));
    }

    @Test
    void deletingSimulation_cascadesToPositions() {
        Simulation simulation = persistSimulation();
        AssetCatalog aapl = persistAssetCatalog("AAPL");
        AssetCatalog tsla = persistAssetCatalog("TSLA");
        persist(simulation.getId(), aapl.getId());
        persist(simulation.getId(), tsla.getId());

        simulationRepository.delete(simulation);
        simulationRepository.flush();

        assertTrue(positionRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void uniqueConstraint_rejectsDuplicateSimulationAndAsset() {
        Simulation simulation = persistSimulation();
        AssetCatalog catalog = persistAssetCatalog("AAPL");
        persist(simulation.getId(), catalog.getId());

        assertThrows(DataIntegrityViolationException.class, () ->
                persist(simulation.getId(), catalog.getId()));
    }

    @Test
    void foreignKeyConstraint_rejectsPositionForUnknownAsset() {
        Simulation simulation = persistSimulation();

        assertThrows(DataIntegrityViolationException.class, () ->
                persist(simulation.getId(), UUID.randomUUID()));
    }

    @Test
    void existsByAssetId_returnsTrueWhenAssetIsReferenced() {
        Simulation simulation = persistSimulation();
        AssetCatalog catalog = persistAssetCatalog("AAPL");
        persist(simulation.getId(), catalog.getId());

        assertTrue(positionRepository.existsByAssetId(catalog.getId()));
    }

    @Test
    void existsByAssetId_returnsFalseWhenAssetIsUnreferenced() {
        AssetCatalog catalog = persistAssetCatalog("AAPL");

        assertFalse(positionRepository.existsByAssetId(catalog.getId()));
    }
}
