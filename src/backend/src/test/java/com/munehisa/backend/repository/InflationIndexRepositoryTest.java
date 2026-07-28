package com.munehisa.backend.repository;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.inflation.InflationIndex;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Tag("integration")
class InflationIndexRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private InflationIndexRepository inflationIndexRepository;

    private InflationIndex persist(InflationCurrency currency, YearMonth month, String index) {
        InflationIndex entry = new InflationIndex();
        entry.setCurrency(currency);
        entry.setReferenceMonth(month);
        entry.setAccumulatedIndex(new BigDecimal(index));
        return inflationIndexRepository.saveAndFlush(entry);
    }

    @Test
    void existsByCurrency() {
        assertFalse(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL));

        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");

        assertTrue(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL));
        assertFalse(inflationIndexRepository.existsByCurrency(InflationCurrency.USD));
    }

    @Test
    void existsByCurrencyAndReferenceMonth() {
        persist(InflationCurrency.USD, YearMonth.of(2024, 3), "300.5");

        assertTrue(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, YearMonth.of(2024, 3)));
        assertFalse(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, YearMonth.of(2024, 4)));
        assertFalse(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.BRL, YearMonth.of(2024, 3)));
    }

    @Test
    void findByCurrency_returnsOnlyRowsForThatCurrency() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");
        persist(InflationCurrency.BRL, YearMonth.of(2024, 2), "102");
        persist(InflationCurrency.USD, YearMonth.of(2024, 1), "300");

        assertEquals(2, inflationIndexRepository.findByCurrency(InflationCurrency.BRL).size());
        assertEquals(1, inflationIndexRepository.findByCurrency(InflationCurrency.USD).size());
    }

    @Test
    void findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_exactMatch() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");
        persist(InflationCurrency.BRL, YearMonth.of(2024, 2), "102");

        Optional<InflationIndex> found = inflationIndexRepository
                .findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(InflationCurrency.BRL, YearMonth.of(2024, 2));

        assertTrue(found.isPresent());
        assertEquals(YearMonth.of(2024, 2), found.get().getReferenceMonth());
    }

    @Test
    void findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_walksBackwardOnGap() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");
        // 2024-02 and 2024-03 are missing (gap)
        persist(InflationCurrency.BRL, YearMonth.of(2024, 4), "105");

        Optional<InflationIndex> found = inflationIndexRepository
                .findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(InflationCurrency.BRL, YearMonth.of(2024, 3));

        assertTrue(found.isPresent());
        assertEquals(YearMonth.of(2024, 1), found.get().getReferenceMonth());
    }

    @Test
    void findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_emptyWhenTargetOlderThanOldest() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");

        Optional<InflationIndex> found = inflationIndexRepository
                .findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(InflationCurrency.BRL, YearMonth.of(2023, 12));

        assertTrue(found.isEmpty());
    }

    @Test
    void findFirstByCurrencyOrderByReferenceMonthAscAndDesc() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");
        persist(InflationCurrency.BRL, YearMonth.of(2024, 6), "110");
        persist(InflationCurrency.BRL, YearMonth.of(2024, 3), "104");

        assertEquals(YearMonth.of(2024, 1), inflationIndexRepository
                .findFirstByCurrencyOrderByReferenceMonthAsc(InflationCurrency.BRL).orElseThrow().getReferenceMonth());
        assertEquals(YearMonth.of(2024, 6), inflationIndexRepository
                .findFirstByCurrencyOrderByReferenceMonthDesc(InflationCurrency.BRL).orElseThrow().getReferenceMonth());
    }

    @Test
    void uniqueConstraint_rejectsDuplicateCurrencyAndReferenceMonth() {
        persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");

        assertThrows(DataIntegrityViolationException.class, () ->
                persist(InflationCurrency.BRL, YearMonth.of(2024, 1), "999"));
    }
}
