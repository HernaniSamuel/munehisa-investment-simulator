package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.user.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Tag("integration")
class SimulationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulationRepository simulationRepository;

    private User persistUser(String email) {
        User user = new User();
        user.setName("Ada Lovelace");
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userRepository.saveAndFlush(user);
    }

    private Simulation persist(UUID userId, String name, String baseCurrency) {
        Simulation simulation = new Simulation();
        simulation.setUserId(userId);
        simulation.setName(name);
        simulation.setBaseCurrency(baseCurrency);
        simulation.setStartMonth(YearMonth.of(2024, 1));
        simulation.setCurrentMonth(YearMonth.of(2024, 6));
        simulation.setCashBalance(new BigDecimal("1000.00"));
        simulation.setTotalAssetValue(new BigDecimal("500.00"));
        return simulationRepository.saveAndFlush(simulation);
    }

    @Test
    void saveAndLoad_roundTrip() {
        User user = persistUser("ada@example.com");

        Simulation saved = persist(user.getId(), "Retirement plan", "BRL");

        Optional<Simulation> found = simulationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        Simulation loaded = found.get();
        assertEquals(user.getId(), loaded.getUserId());
        assertEquals("Retirement plan", loaded.getName());
        assertEquals("BRL", loaded.getBaseCurrency());
        assertEquals(YearMonth.of(2024, 1), loaded.getStartMonth());
        assertEquals(YearMonth.of(2024, 6), loaded.getCurrentMonth());
        assertEquals(0, new BigDecimal("1000.00").compareTo(loaded.getCashBalance()));
        assertEquals(0, new BigDecimal("500.00").compareTo(loaded.getTotalAssetValue()));
    }

    @Test
    void baseCurrencyCheckConstraint_rejectsUnsupportedCurrency() {
        User user = persistUser("ada@example.com");

        assertThrows(DataIntegrityViolationException.class, () ->
                persist(user.getId(), "Retirement plan", "EUR"));
    }

    @Test
    void deletingUser_cascadesToSimulations() {
        User user = persistUser("ada@example.com");
        persist(user.getId(), "Retirement plan", "BRL");
        persist(user.getId(), "Vacation fund", "USD");

        userRepository.delete(user);
        userRepository.flush();

        assertTrue(simulationRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void foreignKeyConstraint_rejectsSimulationForUnknownUser() {
        assertThrows(DataIntegrityViolationException.class, () ->
                persist(UUID.randomUUID(), "Retirement plan", "BRL"));
    }
}
