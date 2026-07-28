package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetMonthlyPriceRepository extends JpaRepository<AssetMonthlyPrice, UUID> {
    List<AssetMonthlyPrice> findByTicker(String ticker);

    List<AssetMonthlyPrice> findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc(
            String ticker, YearMonth month);

    Optional<AssetMonthlyPrice> findFirstByTickerOrderByReferenceMonthDesc(String ticker);
}
