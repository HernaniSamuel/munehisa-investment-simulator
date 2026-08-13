package com.munehisa.backend.service;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
import com.munehisa.backend.dto.AssetLookupResultDTO;
import com.munehisa.backend.dto.dataservice.RawAssetMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.exceptions.AssetDataServiceException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.AssetPredatesStartDateException;
import com.munehisa.backend.exceptions.AssetUnavailableException;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.AssetMonthlyPriceRepository;
import com.munehisa.backend.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Repository derived-query behavior is proven against a real Postgres instance in
 * {@code AssetCatalogRepositoryTest}/{@code AssetMonthlyPriceRepositoryTest}; here both
 * repositories are mocked to simulate that already-proven behavior, so this suite can focus
 * on AssetCacheService's own logic: cold-start/on-demand refresh gating (mirroring
 * ExchangeRateCacheService, not the inflation cache's calendar gate), upsert-without-
 * duplication, and the lower/upper-bound lookup handling (rejection vs. truncation).
 */
@ExtendWith(MockitoExtension.class)
class AssetCacheServiceTest {

    @Mock
    private AssetCatalogRepository assetCatalogRepository;
    @Mock
    private AssetMonthlyPriceRepository assetMonthlyPriceRepository;
    @Mock
    private DataServiceAssetClient dataServiceAssetClient;

    @Mock
    private PositionRepository positionRepository;

    private static final int DEFAULT_GRACE_PERIOD_DAYS = 7;

    private AssetCacheService buildService() {
        return buildService(fixedClockOn(LocalDate.of(2024, 6, 15)), DEFAULT_GRACE_PERIOD_DAYS);
    }

    private AssetCacheService buildService(Clock clock, int orphanGracePeriodDays) {
        AssetCacheService service = new AssetCacheService(
                assetCatalogRepository, assetMonthlyPriceRepository, dataServiceAssetClient, positionRepository, clock);
        ReflectionTestUtils.setField(service, "orphanGracePeriodDays", orphanGracePeriodDays);
        return service;
    }

    private static Clock fixedClockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private AssetCatalog catalog(String ticker, String name, String baseCurrency, LocalDate startDate) {
        return catalog(ticker, name, baseCurrency, startDate, true);
    }

    private AssetCatalog catalog(
            String ticker, String name, String baseCurrency, LocalDate startDate, boolean pricesSplitAdjusted) {
        AssetCatalog catalog = new AssetCatalog();
        catalog.setTicker(ticker);
        catalog.setName(name);
        catalog.setBaseCurrency(baseCurrency);
        catalog.setStartDate(startDate);
        catalog.setPricesSplitAdjusted(pricesSplitAdjusted);
        return catalog;
    }

    private AssetMonthlyPrice row(String ticker, YearMonth month, String price) {
        AssetMonthlyPrice row = new AssetMonthlyPrice();
        row.setTicker(ticker);
        row.setReferenceMonth(month);
        row.setOpen(new BigDecimal(price));
        row.setHigh(new BigDecimal(price));
        row.setLow(new BigDecimal(price));
        row.setClose(new BigDecimal(price));
        row.setVolume(1_000_000L);
        return row;
    }

    private RawAssetMonthDataPoint rawMonth(YearMonth month, String price) {
        return new RawAssetMonthDataPoint(
                month, new BigDecimal(price), new BigDecimal(price), new BigDecimal(price), new BigDecimal(price),
                1_000_000L, null, null);
    }

    // Confirms a single upsert() call for the given ticker/month with the price/volume that
    // rawMonth(...) would produce - the atomic-upsert equivalent of the old assertSame(stale,
    // updated) "same entity, mutated in place" check, since there's no entity to mutate anymore.
    private void verifyMonthlyUpsert(String ticker, YearMonth month, String price) {
        verify(assetMonthlyPriceRepository).upsert(
                any(UUID.class), eq(ticker), eq(month.atDay(1)),
                eq(new BigDecimal(price)), eq(new BigDecimal(price)), eq(new BigDecimal(price)), eq(new BigDecimal(price)),
                eq(1_000_000L), isNull(), isNull());
    }

    // --- Cold start ---------------------------------------------------------------------

    @Test
    void coldStart_fetchesAndPersistsCatalogAndFullHistory() {
        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(false);
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1),
                List.of(rawMonth(YearMonth.of(2024, 1), "180.00"), rawMonth(YearMonth.of(2024, 2), "185.00")), true));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2)))
                .thenReturn(List.of(
                        row("AAPL", YearMonth.of(2024, 1), "180.00"),
                        row("AAPL", YearMonth.of(2024, 2), "185.00")));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("aapl", YearMonth.of(2024, 2));

        verify(assetCatalogRepository).upsert(
                any(UUID.class), eq("AAPL"), eq("Apple Inc."), eq("USD"), eq(LocalDate.of(2024, 1, 1)), eq(true));
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 1), "180.00");
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 2), "185.00");
        assertEquals("AAPL", result.ticker());
        assertEquals("Apple Inc.", result.name());
        assertFalse(result.truncated());
        assertEquals(2, result.series().size());
    }

    @Test
    void coldStart_upstreamFailure_withNoCache_throwsAssetUnavailableException() {
        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(false);
        when(dataServiceAssetClient.fetchSeries("AAPL"))
                .thenThrow(new AssetDataServiceException("AAPL", new RuntimeException("network error")));

        AssetCacheService service = buildService();

        assertThrows(AssetUnavailableException.class, () ->
                service.getAssetSeries("AAPL", YearMonth.of(2024, 6)));
        verify(assetMonthlyPriceRepository, never()).upsert(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void coldStart_unknownTicker_throwsAssetNotFoundException() {
        when(assetCatalogRepository.existsByTicker("NOTATICKER")).thenReturn(false);
        when(dataServiceAssetClient.fetchSeries("NOTATICKER"))
                .thenThrow(new AssetNotFoundException("NOTATICKER", new RuntimeException("404")));

        AssetCacheService service = buildService();

        assertThrows(AssetNotFoundException.class, () ->
                service.getAssetSeries("NOTATICKER", YearMonth.of(2024, 6)));
        verify(assetMonthlyPriceRepository, never()).upsert(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    // --- Lookup bounds -------------------------------------------------------------------

    @Test
    void lookup_returnsFullSeriesFromStartThroughRequestedMonth_notTruncated() {
        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(row("AAPL", YearMonth.of(2024, 3), "190.00")));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2)))
                .thenReturn(List.of(
                        row("AAPL", YearMonth.of(2024, 1), "180.00"),
                        row("AAPL", YearMonth.of(2024, 2), "185.00")));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("AAPL", YearMonth.of(2024, 2));

        assertFalse(result.truncated());
        assertEquals(YearMonth.of(2024, 2), result.returnedMonth());
        assertEquals(2, result.series().size());
        assertEquals(YearMonth.of(2024, 1), result.series().get(0).referenceMonth());
        assertEquals(YearMonth.of(2024, 2), result.series().get(1).referenceMonth());
        verify(dataServiceAssetClient, never()).fetchSeries(anyString());
    }

    @Test
    void lookup_pricesSplitAdjustedReflectsStoredCatalogValue() {
        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1), false)));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2)))
                .thenReturn(List.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("AAPL", YearMonth.of(2024, 2));

        assertFalse(result.pricesSplitAdjusted());
    }

    @Test
    void lookup_beforeStartDate_throwsAssetPredatesStartDateException() {
        when(assetCatalogRepository.existsByTicker("TSLA")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("TSLA"))
                .thenReturn(Optional.of(row("TSLA", YearMonth.of(2024, 6), "200.00")));
        when(assetCatalogRepository.findByTicker("TSLA"))
                .thenReturn(Optional.of(catalog("TSLA", "Tesla Inc.", "USD", LocalDate.of(2010, 6, 1))));

        AssetCacheService service = buildService();

        assertThrows(AssetPredatesStartDateException.class, () ->
                service.getAssetSeries("TSLA", YearMonth.of(1992, 1)));
        verify(dataServiceAssetClient, never()).fetchSeries(anyString());
    }

    @Test
    void lookup_beyondLatestCachedMonth_returnsTruncatedSeriesFlagged() {
        AssetMonthlyPrice juneRow = row("AAPL", YearMonth.of(2024, 6), "200.00");
        AssetCatalog aaplCatalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        // First call is ensureFreshData's gate check (June is latest, target=August fires a
        // refresh attempt); refetch returns no new months (the real calendar month hasn't
        // happened yet), so the second ("post-refresh") call still sees June as latest.
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(juneRow));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(aaplCatalog));
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1),
                List.of(rawMonth(YearMonth.of(2024, 6), "200.00")), true));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 6)))
                .thenReturn(List.of(juneRow));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("AAPL", YearMonth.of(2024, 8));

        assertTrue(result.truncated());
        assertEquals(YearMonth.of(2024, 8), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 6), result.returnedMonth());
        verify(dataServiceAssetClient).fetchSeries("AAPL");
    }

    // --- On-demand refresh -----------------------------------------------------------------

    @Test
    void refresh_firesWhenTargetMonthNewerThanLatestCached() {
        AssetMonthlyPrice juneRow = row("AAPL", YearMonth.of(2024, 6), "200.00");
        AssetMonthlyPrice julyRow = row("AAPL", YearMonth.of(2024, 7), "205.00");
        AssetCatalog aaplCatalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(juneRow), Optional.of(julyRow));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(aaplCatalog));
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1),
                List.of(rawMonth(YearMonth.of(2024, 6), "200.00"), rawMonth(YearMonth.of(2024, 7), "205.00")), true));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 7)))
                .thenReturn(List.of(juneRow, julyRow));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("AAPL", YearMonth.of(2024, 7));

        verify(dataServiceAssetClient).fetchSeries("AAPL");
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 6), "200.00");
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 7), "205.00");
        assertFalse(result.truncated());
        assertEquals(YearMonth.of(2024, 7), result.returnedMonth());
    }

    @Test
    void refresh_doesNotFireWhenTargetMonthNotNewerThanLatestCached() {
        AssetMonthlyPrice juneRow = row("AAPL", YearMonth.of(2024, 6), "200.00");

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(juneRow));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 6)))
                .thenReturn(List.of(juneRow));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = service.getAssetSeries("AAPL", YearMonth.of(2024, 6));

        assertFalse(result.truncated());
        verify(dataServiceAssetClient, never()).fetchSeries(anyString());
        verify(assetMonthlyPriceRepository, never()).upsert(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void refresh_upsertsWithoutDuplicatingExistingRows() {
        AssetMonthlyPrice staleJuneRow = row("AAPL", YearMonth.of(2024, 6), "199.00"); // pre-revision
        AssetMonthlyPrice julyRow = row("AAPL", YearMonth.of(2024, 7), "205.00");
        AssetCatalog aaplCatalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(staleJuneRow), Optional.of(julyRow));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(aaplCatalog));
        when(dataServiceAssetClient.fetchSeries("AAPL")).thenReturn(new RawAssetSeries(
                "AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1),
                List.of(rawMonth(YearMonth.of(2024, 6), "200.00"), rawMonth(YearMonth.of(2024, 7), "205.00")), true));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 7)))
                .thenReturn(List.of(staleJuneRow, julyRow));

        AssetCacheService service = buildService();
        service.getAssetSeries("AAPL", YearMonth.of(2024, 7));

        // exactly one upsert call per month - the revised June value overwrites the stale
        // one atomically (ON CONFLICT DO UPDATE) rather than inserting a duplicate row
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 6), "200.00");
        verifyMonthlyUpsert("AAPL", YearMonth.of(2024, 7), "205.00");
        verify(assetMonthlyPriceRepository, times(2)).upsert(
                any(UUID.class), eq("AAPL"), any(LocalDate.class), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void refresh_dataServiceExceptionDuringRefresh_stillServesStaleData() {
        AssetMonthlyPrice juneRow = row("AAPL", YearMonth.of(2024, 6), "200.00");

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(juneRow));
        when(dataServiceAssetClient.fetchSeries("AAPL"))
                .thenThrow(new AssetDataServiceException("AAPL", new RuntimeException("network error")));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 6)))
                .thenReturn(List.of(juneRow));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = assertDoesNotThrow(() ->
                service.getAssetSeries("AAPL", YearMonth.of(2024, 7)));

        assertTrue(result.truncated());
        assertEquals(YearMonth.of(2024, 6), result.returnedMonth());
        verify(assetMonthlyPriceRepository, never()).upsert(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void refresh_notFoundDuringRefresh_stillServesStaleData() {
        AssetMonthlyPrice juneRow = row("AAPL", YearMonth.of(2024, 6), "200.00");

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(juneRow));
        when(dataServiceAssetClient.fetchSeries("AAPL"))
                .thenThrow(new AssetNotFoundException("AAPL", new RuntimeException("404")));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 6)))
                .thenReturn(List.of(juneRow));

        AssetCacheService service = buildService();
        AssetLookupResultDTO result = assertDoesNotThrow(() ->
                service.getAssetSeries("AAPL", YearMonth.of(2024, 7)));

        assertTrue(result.truncated());
        assertEquals(YearMonth.of(2024, 6), result.returnedMonth());
        verify(assetMonthlyPriceRepository, never()).upsert(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    // --- Eviction (marking orphaned) --------------------------------------------------------

    @Test
    void evictIfOrphaned_marksOrphanedSinceWhenNoPositionReferencesIt() {
        UUID assetId = UUID.randomUUID();
        AssetCatalog catalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));
        Clock clock = fixedClockOn(LocalDate.of(2024, 6, 15));
        when(positionRepository.existsByAssetId(assetId)).thenReturn(false);
        when(assetCatalogRepository.findById(assetId)).thenReturn(Optional.of(catalog));

        AssetCacheService service = buildService(clock, DEFAULT_GRACE_PERIOD_DAYS);
        service.evictIfOrphaned(assetId);

        assertEquals(clock.instant(), catalog.getOrphanedSince());
        verify(assetCatalogRepository).save(catalog);
        verify(assetCatalogRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void evictIfOrphaned_secondCallDoesNotOverwriteExistingOrphanedSince() {
        UUID assetId = UUID.randomUUID();
        Instant alreadyOrphanedSince = fixedClockOn(LocalDate.of(2024, 6, 1)).instant();
        AssetCatalog catalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));
        catalog.setOrphanedSince(alreadyOrphanedSince);
        when(positionRepository.existsByAssetId(assetId)).thenReturn(false);
        when(assetCatalogRepository.findById(assetId)).thenReturn(Optional.of(catalog));

        AssetCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 6, 15)), DEFAULT_GRACE_PERIOD_DAYS);
        service.evictIfOrphaned(assetId);

        assertEquals(alreadyOrphanedSince, catalog.getOrphanedSince());
        verify(assetCatalogRepository, never()).save(any(AssetCatalog.class));
    }

    @Test
    void evictIfOrphaned_leavesCatalogRowWhenAPositionStillReferencesIt() {
        UUID assetId = UUID.randomUUID();
        when(positionRepository.existsByAssetId(assetId)).thenReturn(true);

        AssetCacheService service = buildService();
        service.evictIfOrphaned(assetId);

        verify(assetCatalogRepository, never()).findById(any(UUID.class));
        verify(assetCatalogRepository, never()).deleteById(any(UUID.class));
    }

    // --- Un-orphaning on lookup --------------------------------------------------------------

    @Test
    void lookup_clearsOrphanedSinceWhenTickerRowWasOrphaned() {
        AssetCatalog catalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));
        catalog.setOrphanedSince(fixedClockOn(LocalDate.of(2024, 6, 1)).instant());

        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));
        when(assetCatalogRepository.findByTicker("AAPL")).thenReturn(Optional.of(catalog));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2)))
                .thenReturn(List.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));

        AssetCacheService service = buildService();
        service.getAssetSeries("AAPL", YearMonth.of(2024, 2));

        assertNull(catalog.getOrphanedSince());
        verify(assetCatalogRepository).save(catalog);
    }

    @Test
    void lookup_doesNotSaveWhenOrphanedSinceAlreadyNull() {
        when(assetCatalogRepository.existsByTicker("AAPL")).thenReturn(true);
        when(assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc("AAPL"))
                .thenReturn(Optional.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));
        when(assetCatalogRepository.findByTicker("AAPL"))
                .thenReturn(Optional.of(catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1))));
        when(assetMonthlyPriceRepository.findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc("AAPL", YearMonth.of(2024, 2)))
                .thenReturn(List.of(row("AAPL", YearMonth.of(2024, 2), "185.00")));

        AssetCacheService service = buildService();
        service.getAssetSeries("AAPL", YearMonth.of(2024, 2));

        verify(assetCatalogRepository, never()).save(any(AssetCatalog.class));
    }

    // --- Cleanup job -------------------------------------------------------------------------

    @Test
    void cleanup_deletesRowOrphanedPastGracePeriodWithNoPosition() {
        Clock clock = fixedClockOn(LocalDate.of(2024, 6, 15));
        AssetCatalog catalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));
        UUID assetId = UUID.randomUUID();
        catalog.setId(assetId);
        catalog.setOrphanedSince(clock.instant().minus(10, ChronoUnit.DAYS));

        Instant expectedCutoff = clock.instant().minus(DEFAULT_GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        when(assetCatalogRepository.findByOrphanedSinceBefore(expectedCutoff)).thenReturn(List.of(catalog));
        when(positionRepository.existsByAssetId(assetId)).thenReturn(false);

        AssetCacheService service = buildService(clock, DEFAULT_GRACE_PERIOD_DAYS);
        service.cleanupOrphanedAssets();

        verify(assetCatalogRepository).delete(catalog);
    }

    @Test
    void cleanup_keepsRowOrphanedPastGracePeriodButNowReferencedByPosition() {
        Clock clock = fixedClockOn(LocalDate.of(2024, 6, 15));
        AssetCatalog catalog = catalog("AAPL", "Apple Inc.", "USD", LocalDate.of(2024, 1, 1));
        UUID assetId = UUID.randomUUID();
        catalog.setId(assetId);
        catalog.setOrphanedSince(clock.instant().minus(10, ChronoUnit.DAYS));

        Instant expectedCutoff = clock.instant().minus(DEFAULT_GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        when(assetCatalogRepository.findByOrphanedSinceBefore(expectedCutoff)).thenReturn(List.of(catalog));
        when(positionRepository.existsByAssetId(assetId)).thenReturn(true);

        AssetCacheService service = buildService(clock, DEFAULT_GRACE_PERIOD_DAYS);
        service.cleanupOrphanedAssets();

        verify(assetCatalogRepository, never()).delete(any(AssetCatalog.class));
    }

    @Test
    void cleanup_usesConfiguredGracePeriodDaysAsCutoff() {
        Clock clock = fixedClockOn(LocalDate.of(2024, 6, 15));
        int customGracePeriodDays = 14;
        Instant expectedCutoff = clock.instant().minus(customGracePeriodDays, ChronoUnit.DAYS);
        when(assetCatalogRepository.findByOrphanedSinceBefore(expectedCutoff)).thenReturn(List.of());

        AssetCacheService service = buildService(clock, customGracePeriodDays);
        service.cleanupOrphanedAssets();

        verify(assetCatalogRepository).findByOrphanedSinceBefore(expectedCutoff);
    }
}
