package com.munehisa.backend.repository;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
