package com.munehisa.backend.repository;

import com.munehisa.backend.domain.simulation.Simulation;
import com.munehisa.backend.domain.simulation.Transaction;
import com.munehisa.backend.domain.simulation.TransactionType;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class TransactionRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private TransactionRepository transactionRepository;

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

    private Transaction persistTrade(UUID simulationId, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulationId);
        transaction.setType(type);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("1800.00"));
        transaction.setTicker("AAPL");
        transaction.setAssetName("Apple Inc.");
        transaction.setQuantity(BigDecimal.valueOf(10));
        return transactionRepository.saveAndFlush(transaction);
    }

    private Transaction persistCashMovement(UUID simulationId, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulationId);
        transaction.setType(type);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("250.00"));
        return transactionRepository.saveAndFlush(transaction);
    }

    private Transaction persistDividend(UUID simulationId) {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulationId);
        transaction.setType(TransactionType.DIVIDEND);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("12.50"));
        transaction.setTicker("AAPL");
        transaction.setAssetName("Apple Inc.");
        return transactionRepository.saveAndFlush(transaction);
    }

    @Test
    void saveAndLoad_roundTrip_buy() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistTrade(simulation.getId(), TransactionType.BUY);

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(simulation.getId(), loaded.getSimulationId());
        assertEquals(TransactionType.BUY, loaded.getType());
        assertEquals(YearMonth.of(2024, 6), loaded.getMonth());
        assertEquals(0, new BigDecimal("1800.00").compareTo(loaded.getAmount()));
        assertEquals("AAPL", loaded.getTicker());
        assertEquals("Apple Inc.", loaded.getAssetName());
        assertEquals(0, new BigDecimal("10").compareTo(loaded.getQuantity()));
    }

    @Test
    void saveAndLoad_roundTrip_sell() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistTrade(simulation.getId(), TransactionType.SELL);

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransactionType.SELL, loaded.getType());
        assertEquals("AAPL", loaded.getTicker());
        assertEquals("Apple Inc.", loaded.getAssetName());
        assertEquals(0, new BigDecimal("10").compareTo(loaded.getQuantity()));
    }

    @Test
    void saveAndLoad_roundTrip_deposit() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistCashMovement(simulation.getId(), TransactionType.DEPOSIT);

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransactionType.DEPOSIT, loaded.getType());
        assertEquals(0, new BigDecimal("250.00").compareTo(loaded.getAmount()));
        assertNull(loaded.getTicker());
        assertNull(loaded.getAssetName());
        assertNull(loaded.getQuantity());
    }

    @Test
    void saveAndLoad_roundTrip_withdrawal() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistCashMovement(simulation.getId(), TransactionType.WITHDRAWAL);

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransactionType.WITHDRAWAL, loaded.getType());
        assertNull(loaded.getTicker());
        assertNull(loaded.getAssetName());
        assertNull(loaded.getQuantity());
    }

    @Test
    void saveAndLoad_roundTrip_dividend() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistDividend(simulation.getId());

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TransactionType.DIVIDEND, loaded.getType());
        assertEquals("AAPL", loaded.getTicker());
        assertEquals("Apple Inc.", loaded.getAssetName());
        assertNull(loaded.getQuantity());
        assertEquals(0, new BigDecimal("12.50").compareTo(loaded.getAmount()));
    }

    @Test
    void deletingSimulation_cascadesToTransactions() {
        Simulation simulation = persistSimulation();
        persistTrade(simulation.getId(), TransactionType.BUY);
        persistCashMovement(simulation.getId(), TransactionType.DEPOSIT);

        simulationRepository.delete(simulation);
        simulationRepository.flush();

        assertTrue(transactionRepository.findBySimulationId(simulation.getId()).isEmpty());
    }

    @Test
    void checkConstraint_rejectsBuyWithNullQuantity() {
        Simulation simulation = persistSimulation();

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.BUY);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("1800.00"));
        transaction.setTicker("AAPL");
        transaction.setAssetName("Apple Inc.");
        // quantity left null: BUY/SELL require it

        assertThrows(DataIntegrityViolationException.class, () ->
                transactionRepository.saveAndFlush(transaction));
    }

    @Test
    void checkConstraint_rejectsDepositWithNonNullTicker() {
        Simulation simulation = persistSimulation();

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setTicker("AAPL"); // DEPOSIT/WITHDRAWAL must not carry a ticker

        assertThrows(DataIntegrityViolationException.class, () ->
                transactionRepository.saveAndFlush(transaction));
    }

    @Test
    void checkConstraint_rejectsDividendWithNullTicker() {
        Simulation simulation = persistSimulation();

        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulation.getId());
        transaction.setType(TransactionType.DIVIDEND);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("12.50"));
        // ticker/assetName left null: DIVIDEND requires them

        assertThrows(DataIntegrityViolationException.class, () ->
                transactionRepository.saveAndFlush(transaction));
    }

    @Test
    void foreignKeyConstraint_rejectsTransactionForUnknownSimulation() {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(UUID.randomUUID());
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setMonth(YearMonth.of(2024, 6));
        transaction.setAmount(new BigDecimal("250.00"));

        assertThrows(DataIntegrityViolationException.class, () ->
                transactionRepository.saveAndFlush(transaction));
    }

    @Test
    void save_populatesCreatedAtAutomatically() {
        Simulation simulation = persistSimulation();

        Transaction saved = persistTrade(simulation.getId(), TransactionType.BUY);

        Transaction loaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(loaded.getCreatedAt());
    }

    // --- findBySimulationIdOrderByMonthDescCreatedAtAsc ------------------------------------

    @Test
    void findOrdered_multipleMonths_sortedByMonthDescending() {
        Simulation simulation = persistSimulation();
        persistCashMovementInMonth(simulation.getId(), YearMonth.of(2024, 3));
        persistCashMovementInMonth(simulation.getId(), YearMonth.of(2024, 6));
        persistCashMovementInMonth(simulation.getId(), YearMonth.of(2024, 1));

        List<Transaction> results = transactionRepository.findBySimulationIdOrderByMonthDescCreatedAtAsc(simulation.getId());

        assertEquals(3, results.size());
        assertEquals(YearMonth.of(2024, 6), results.get(0).getMonth());
        assertEquals(YearMonth.of(2024, 3), results.get(1).getMonth());
        assertEquals(YearMonth.of(2024, 1), results.get(2).getMonth());
    }

    @Test
    void findOrdered_sameMonth_sortedByCreatedAtAscending() {
        Simulation simulation = persistSimulation();
        Transaction first = persistCashMovementInMonth(simulation.getId(), YearMonth.of(2024, 6));
        Transaction second = persistCashMovementInMonth(simulation.getId(), YearMonth.of(2024, 6));

        List<Transaction> results = transactionRepository.findBySimulationIdOrderByMonthDescCreatedAtAsc(simulation.getId());

        assertEquals(2, results.size());
        assertEquals(first.getId(), results.get(0).getId());
        assertEquals(second.getId(), results.get(1).getId());
        assertTrue(results.get(0).getCreatedAt().compareTo(results.get(1).getCreatedAt()) <= 0);
    }

    private Transaction persistCashMovementInMonth(UUID simulationId, YearMonth month) {
        Transaction transaction = new Transaction();
        transaction.setSimulationId(simulationId);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setMonth(month);
        transaction.setAmount(new BigDecimal("250.00"));
        return transactionRepository.saveAndFlush(transaction);
    }
}
