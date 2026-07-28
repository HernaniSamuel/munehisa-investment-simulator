package com.munehisa.backend.repository;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
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
class ExchangeRateRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private ExchangeRate persist(String base, String quote, YearMonth month, String ohlc) {
        ExchangeRate entry = new ExchangeRate();
        entry.setBaseCurrency(base);
        entry.setQuoteCurrency(quote);
        entry.setReferenceMonth(month);
        entry.setOpen(new BigDecimal(ohlc));
        entry.setHigh(new BigDecimal(ohlc));
        entry.setLow(new BigDecimal(ohlc));
        entry.setClose(new BigDecimal(ohlc));
        return exchangeRateRepository.saveAndFlush(entry);
    }

    @Test
    void existsByBaseCurrencyAndQuoteCurrency() {
        assertFalse(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD"));

        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");

        assertTrue(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD"));
        assertFalse(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("EUR", "USD"));
    }

    @Test
    void findByBaseCurrencyAndQuoteCurrency_returnsOnlyRowsForThatPair() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");
        persist("BRL", "USD", YearMonth.of(2024, 2), "5.20");
        persist("EUR", "USD", YearMonth.of(2024, 1), "1.10");

        assertEquals(2, exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("BRL", "USD").size());
        assertEquals(1, exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency("EUR", "USD").size());
    }

    @Test
    void findFirstByPairAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_exactMatch() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");
        persist("BRL", "USD", YearMonth.of(2024, 2), "5.20");

        Optional<ExchangeRate> found = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                        "BRL", "USD", YearMonth.of(2024, 2));

        assertTrue(found.isPresent());
        assertEquals(YearMonth.of(2024, 2), found.get().getReferenceMonth());
    }

    @Test
    void findFirstByPairAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_walksBackwardOnGap() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");
        // 2024-02 and 2024-03 are missing (gap)
        persist("BRL", "USD", YearMonth.of(2024, 4), "5.30");

        Optional<ExchangeRate> found = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                        "BRL", "USD", YearMonth.of(2024, 3));

        assertTrue(found.isPresent());
        assertEquals(YearMonth.of(2024, 1), found.get().getReferenceMonth());
    }

    @Test
    void findFirstByPairAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc_emptyWhenTargetOlderThanOldest() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");

        Optional<ExchangeRate> found = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                        "BRL", "USD", YearMonth.of(2023, 12));

        assertTrue(found.isEmpty());
    }

    @Test
    void findFirstByPairOrderByReferenceMonthAscAndDesc() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");
        persist("BRL", "USD", YearMonth.of(2024, 6), "5.60");
        persist("BRL", "USD", YearMonth.of(2024, 3), "5.30");

        assertEquals(YearMonth.of(2024, 1), exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc("BRL", "USD")
                .orElseThrow().getReferenceMonth());
        assertEquals(YearMonth.of(2024, 6), exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc("BRL", "USD")
                .orElseThrow().getReferenceMonth());
    }

    @Test
    void uniqueConstraint_rejectsDuplicatePairAndReferenceMonth() {
        persist("BRL", "USD", YearMonth.of(2024, 1), "5.10");

        assertThrows(DataIntegrityViolationException.class, () ->
                persist("BRL", "USD", YearMonth.of(2024, 1), "9.99"));
    }

    @Test
    void canonicalPairCheckConstraint_rejectsNonCanonicalDirection() {
        // canonical order requires base_currency < quote_currency alphabetically;
        // "USD"/"BRL" is the reversed, non-canonical direction of "BRL"/"USD"
        assertThrows(DataIntegrityViolationException.class, () ->
                persist("USD", "BRL", YearMonth.of(2024, 1), "0.20"));
    }
}
