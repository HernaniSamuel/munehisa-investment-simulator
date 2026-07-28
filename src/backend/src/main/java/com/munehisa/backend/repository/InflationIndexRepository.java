package com.munehisa.backend.repository;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.inflation.InflationIndex;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
