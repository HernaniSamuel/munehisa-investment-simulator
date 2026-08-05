package com.munehisa.backend.repository;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.inflation.InflationIndex;
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

public interface InflationIndexRepository extends JpaRepository<InflationIndex, UUID> {
    boolean existsByCurrency(InflationCurrency currency);

    boolean existsByCurrencyAndReferenceMonth(InflationCurrency currency, YearMonth referenceMonth);

    List<InflationIndex> findByCurrency(InflationCurrency currency);

    Optional<InflationIndex> findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
            InflationCurrency currency, YearMonth targetMonth);

    Optional<InflationIndex> findFirstByCurrencyOrderByReferenceMonthAsc(InflationCurrency currency);

    Optional<InflationIndex> findFirstByCurrencyOrderByReferenceMonthDesc(InflationCurrency currency);

    // Atomic insert-or-update on the natural key (currency, reference_month). Called once per
    // month in a refresh's normalized series, so two concurrent refreshes for the same
    // never-cached currency can never collide on uk_inflation_index_currency_reference_month.
    // currency is bound as its enum name (String) - native query parameter binding does not
    // go through the entity's @Enumerated(STRING) mapping.
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO inflation_index (id, currency, reference_month, accumulated_index)
            VALUES (:id, :currency, :referenceMonth, :accumulatedIndex)
            ON CONFLICT (currency, reference_month) DO UPDATE SET
                accumulated_index = EXCLUDED.accumulated_index
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("currency") String currency,
            @Param("referenceMonth") LocalDate referenceMonth,
            @Param("accumulatedIndex") BigDecimal accumulatedIndex);
}
