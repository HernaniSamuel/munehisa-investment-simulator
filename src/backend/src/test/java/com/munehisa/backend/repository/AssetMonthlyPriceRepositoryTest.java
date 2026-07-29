package com.munehisa.backend.repository;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
class AssetMonthlyPriceRepositoryTest extends SharedPostgresContainer {

    @Autowired
    private AssetCatalogRepository assetCatalogRepository;

    @Autowired
    private AssetMonthlyPriceRepository assetMonthlyPriceRepository;

    private void persistCatalog(String ticker) {
        AssetCatalog catalog = new AssetCatalog();
        catalog.setTicker(ticker);
        catalog.setName(ticker + " Inc.");
        catalog.setBaseCurrency("USD");
        catalog.setStartDate(LocalDate.of(2024, 1, 1));
        assetCatalogRepository.saveAndFlush(catalog);
    }

    private AssetMonthlyPrice persist(String ticker, YearMonth month, String price) {
        AssetMonthlyPrice row = new AssetMonthlyPrice();
        row.setTicker(ticker);
        row.setReferenceMonth(month);
        row.setOpen(new BigDecimal(price));
        row.setHigh(new BigDecimal(price));
        row.setLow(new BigDecimal(price));
        row.setClose(new BigDecimal(price));
        row.setVolume(1_000_000L);
        return assetMonthlyPriceRepository.saveAndFlush(row);
    }

    @Test
    void findByTicker_returnsOnlyRowsForThatTicker() {
        persistCatalog("AAPL");
        persistCatalog("TSLA");
        persist("AAPL", YearMonth.of(2024, 1), "180.00");
        persist("AAPL", YearMonth.of(2024, 2), "185.00");
        persist("TSLA", YearMonth.of(2024, 1), "200.00");

        assertEquals(2, assetMonthlyPriceRepository.findByTicker("AAPL").size());
        assertEquals(1, assetMonthlyPriceRepository.findByTicker("TSLA").size());
    }

    @Test
    void findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc_exactMatchAndAscendingOrder() {
        persistCatalog("AAPL");
        persist("AAPL", YearMonth.of(2024, 1), "180.00");
        persist("AAPL", YearMonth.of(2024, 2), "185.00");

        List<AssetMonthlyPrice> series = assetMonthlyPriceRepository
                .findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2));

        assertEquals(2, series.size());
        assertEquals(YearMonth.of(2024, 1), series.get(0).getReferenceMonth());
        assertEquals(YearMonth.of(2024, 2), series.get(1).getReferenceMonth());
    }

    @Test
    void findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc_excludesMonthsAfterTarget() {
        persistCatalog("AAPL");
        persist("AAPL", YearMonth.of(2024, 1), "180.00");
        persist("AAPL", YearMonth.of(2024, 2), "185.00");
        persist("AAPL", YearMonth.of(2024, 3), "190.00");

        List<AssetMonthlyPrice> series = assetMonthlyPriceRepository
                .findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2));

        assertEquals(2, series.size());
        assertTrue(series.stream().noneMatch(row -> row.getReferenceMonth().equals(YearMonth.of(2024, 3))));
    }

    @Test
    void findFirstByTickerOrderByReferenceMonthDesc_returnsLatestMonth() {
        persistCatalog("AAPL");
        persist("AAPL", YearMonth.of(2024, 1), "180.00");
        persist("AAPL", YearMonth.of(2024, 6), "195.00");
        persist("AAPL", YearMonth.of(2024, 3), "185.00");

        Optional<AssetMonthlyPrice> latest = assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL");

        assertTrue(latest.isPresent());
        assertEquals(YearMonth.of(2024, 6), latest.get().getReferenceMonth());
    }

    @Test
    void uniqueConstraint_rejectsDuplicateTickerAndReferenceMonth() {
        persistCatalog("AAPL");
        persist("AAPL", YearMonth.of(2024, 1), "180.00");

        assertThrows(DataIntegrityViolationException.class, () ->
                persist("AAPL", YearMonth.of(2024, 1), "999.00"));
    }

    @Test
    void foreignKeyConstraint_rejectsRowForUnknownTicker() {
        assertThrows(DataIntegrityViolationException.class, () ->
                persist("GOOG", YearMonth.of(2024, 1), "140.00"));
    }
}
