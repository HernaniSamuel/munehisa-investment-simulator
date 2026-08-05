package com.munehisa.backend.repository;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    boolean existsByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    List<ExchangeRate> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
            String baseCurrency, String quoteCurrency, YearMonth targetMonth);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc(
            String baseCurrency, String quoteCurrency);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc(
            String baseCurrency, String quoteCurrency);

    // Atomic insert-or-update on the natural key (base_currency, quote_currency,
    // reference_month). Called once per month in a refresh's raw series, so two concurrent
    // refreshes for the same never-cached pair can never collide on
    // uk_exchange_rate_pair_reference_month.
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO exchange_rate (id, base_currency, quote_currency, reference_month, open, high, low, close)
            VALUES (:id, :baseCurrency, :quoteCurrency, :referenceMonth, :open, :high, :low, :close)
            ON CONFLICT (base_currency, quote_currency, reference_month) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("baseCurrency") String baseCurrency,
            @Param("quoteCurrency") String quoteCurrency,
            @Param("referenceMonth") LocalDate referenceMonth,
            @Param("open") BigDecimal open,
            @Param("high") BigDecimal high,
            @Param("low") BigDecimal low,
            @Param("close") BigDecimal close);
}
