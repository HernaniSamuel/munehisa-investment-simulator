package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
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

public interface AssetMonthlyPriceRepository extends JpaRepository<AssetMonthlyPrice, UUID> {
    List<AssetMonthlyPrice> findByTicker(String ticker);

    List<AssetMonthlyPrice> findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc(
            String ticker, YearMonth month);

    Optional<AssetMonthlyPrice> findFirstByTickerOrderByReferenceMonthDesc(String ticker);

    // Atomic insert-or-update on the natural key (ticker, reference_month). Called once per
    // month in a refresh's raw series, so two concurrent refreshes for the same never-cached
    // ticker can never collide on uk_asset_monthly_price_ticker_reference_month.
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO asset_monthly_price
                (id, ticker, reference_month, open, high, low, close, volume, dividends, splits)
            VALUES
                (:id, :ticker, :referenceMonth, :open, :high, :low, :close, :volume, :dividends, :splits)
            ON CONFLICT (ticker, reference_month) DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                dividends = EXCLUDED.dividends,
                splits = EXCLUDED.splits
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("ticker") String ticker,
            @Param("referenceMonth") LocalDate referenceMonth,
            @Param("open") BigDecimal open,
            @Param("high") BigDecimal high,
            @Param("low") BigDecimal low,
            @Param("close") BigDecimal close,
            @Param("volume") long volume,
            @Param("dividends") BigDecimal dividends,
            @Param("splits") BigDecimal splits);
}
