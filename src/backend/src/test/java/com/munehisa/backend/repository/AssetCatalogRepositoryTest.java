package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.testsupport.SharedPostgresContainer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class AssetCatalogRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private AssetCatalogRepository assetCatalogRepository;

    @Autowired
    private EntityManager entityManager;

    private AssetCatalog persist(String ticker, String name, String baseCurrency, LocalDate startDate) {
        AssetCatalog catalog = new AssetCatalog();
        catalog.setTicker(ticker);
        catalog.setName(name);
        catalog.setBaseCurrency(baseCurrency);
        catalog.setStartDate(startDate);
        return assetCatalogRepository.saveAndFlush(catalog);
    }

    @Test
    void existsByTicker() {
        assertFalse(assetCatalogRepository.existsByTicker("AAPL"));

        persist("AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1));

        assertTrue(assetCatalogRepository.existsByTicker("AAPL"));
        assertFalse(assetCatalogRepository.existsByTicker("TSLA"));
    }

    @Test
    void findByTicker_returnsPersistedCatalogRow() {
        persist("AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1));

        Optional<AssetCatalog> found = assetCatalogRepository.findByTicker("AAPL");

        assertTrue(found.isPresent());
        assertEquals("Apple Inc.", found.get().getName());
        assertEquals("USD", found.get().getBaseCurrency());
        assertEquals(LocalDate.of(1980, 12, 1), found.get().getStartDate());
    }

    @Test
    void findByTicker_emptyWhenTickerNeverCached() {
        assertTrue(assetCatalogRepository.findByTicker("TSLA").isEmpty());
    }

    @Test
    void uniqueConstraint_rejectsDuplicateTicker() {
        persist("AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1));

        assertThrows(DataIntegrityViolationException.class, () ->
                persist("AAPL", "Apple Inc. (renamed)", "USD", LocalDate.of(1980, 12, 1)));
    }

    @Test
    void persist_defaultsPricesSplitAdjustedToTrue() {
        persist("AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1));

        assertTrue(assetCatalogRepository.findByTicker("AAPL").orElseThrow().isPricesSplitAdjusted());
    }

    @Test
    void upsert_insertsThenRefreshesPricesSplitAdjustedOnConflict() {
        assetCatalogRepository.upsert(
                UUID.randomUUID(), "AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1), true);

        assertTrue(assetCatalogRepository.findByTicker("AAPL").orElseThrow().isPricesSplitAdjusted());

        // The native upsert() bypasses the persistence context, so the entity loaded by the
        // findByTicker() above is now stale in Hibernate's first-level cache - clear it so the
        // next findByTicker() below actually re-reads the row this second upsert() just wrote,
        // instead of returning the still-managed (and now wrong) instance from above.
        entityManager.clear();
        assetCatalogRepository.upsert(
                UUID.randomUUID(), "AAPL", "Apple Inc.", "USD", LocalDate.of(1980, 12, 1), false);

        assertFalse(assetCatalogRepository.findByTicker("AAPL").orElseThrow().isPricesSplitAdjusted());
    }
}
