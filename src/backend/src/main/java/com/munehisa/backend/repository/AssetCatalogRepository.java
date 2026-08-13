package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetCatalogRepository extends JpaRepository<AssetCatalog, UUID> {
    boolean existsByTicker(String ticker);

    Optional<AssetCatalog> findByTicker(String ticker);

    // NULL orphaned_since is excluded automatically: SQL "<" against NULL evaluates to
    // unknown/false, so still-referenced (never orphaned) rows never match this comparison.
    List<AssetCatalog> findByOrphanedSinceBefore(Instant cutoff);

    // Atomic insert-or-update on the natural key (ticker), so two concurrent cold-start
    // refreshes for the same never-cached ticker can never both attempt an insert and
    // collide on uk_asset_catalog_ticker - Postgres serializes the conflicting inserts
    // itself. id is only used on the insert branch; an existing row keeps its own id.
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO asset_catalog (id, ticker, name, base_currency, start_date, prices_split_adjusted)
            VALUES (:id, :ticker, :name, :baseCurrency, :startDate, :pricesSplitAdjusted)
            ON CONFLICT (ticker) DO UPDATE SET
                name = EXCLUDED.name,
                base_currency = EXCLUDED.base_currency,
                start_date = EXCLUDED.start_date,
                prices_split_adjusted = EXCLUDED.prices_split_adjusted
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("ticker") String ticker,
            @Param("name") String name,
            @Param("baseCurrency") String baseCurrency,
            @Param("startDate") LocalDate startDate,
            @Param("pricesSplitAdjusted") boolean pricesSplitAdjusted);
}
