package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Tag("integration")
class AssetCatalogRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AssetCatalogRepository assetCatalogRepository;

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
}
