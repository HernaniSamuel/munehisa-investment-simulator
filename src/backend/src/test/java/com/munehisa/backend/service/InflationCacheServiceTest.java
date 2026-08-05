package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.inflation.InflationIndex;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.dataservice.RawInflationDataPoint;
import com.munehisa.backend.exceptions.InflationDataServiceException;
import com.munehisa.backend.exceptions.InflationUnavailableException;
import com.munehisa.backend.repository.InflationIndexRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Repository derived-query behavior (backward-walk, oldest/newest, unique constraint) is
 * proven against a real Postgres instance in {@code InflationIndexRepositoryTest}; here the
 * repository is mocked to simulate that already-proven behavior, so this suite can focus on
 * InflationCacheService's own logic: cold-start/refresh gating, BRL->index normalization,
 * upsert-without-duplication, and the lookup-fallback flags.
 */
@ExtendWith(MockitoExtension.class)
class InflationCacheServiceTest {

    @Mock
    private InflationIndexRepository inflationIndexRepository;
    @Mock
    private DataServiceInflationClient dataServiceInflationClient;

    private InflationCacheService buildService(Clock clock, int refreshThresholdDay) {
        InflationCacheService service = new InflationCacheService(inflationIndexRepository, dataServiceInflationClient, clock);
        ReflectionTestUtils.setField(service, "refreshThresholdDay", refreshThresholdDay);
        return service;
    }

    private static Clock fixedClockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private InflationIndex row(InflationCurrency currency, YearMonth month, String index) {
        InflationIndex entry = new InflationIndex();
        entry.setCurrency(currency);
        entry.setReferenceMonth(month);
        entry.setAccumulatedIndex(new BigDecimal(index));
        return entry;
    }

    private BigDecimal indexFor(List<InflationIndex> rows, YearMonth month) {
        return rows.stream()
                .filter(r -> r.getReferenceMonth().equals(month))
                .findFirst()
                .orElseThrow()
                .getAccumulatedIndex();
    }

    // Confirms a single upsert() call for the given currency/month/index - the atomic-upsert
    // equivalent of the old assertSame(stale, updated) "same entity, mutated in place" check,
    // since there's no entity to mutate anymore. The index is compared with compareTo (not
    // eq()) because BRL's chained BigDecimal multiplication under MathContext(50) can produce
    // a different scale than a literal BigDecimal of the same numeric value.
    private void verifyMonthlyUpsert(InflationCurrency currency, YearMonth month, String index) {
        verify(inflationIndexRepository).upsert(
                any(UUID.class), eq(currency.name()), eq(month.atDay(1)),
                argThat(actual -> actual.compareTo(new BigDecimal(index)) == 0));
    }

    /** Stubs the three lookup queries against an in-memory row set, mirroring what the real
     *  derived queries (proven in InflationIndexRepositoryTest) would return for that data. */
    private void stubLookupQueries(InflationCurrency currency, YearMonth targetMonth, List<InflationIndex> rows) {
        List<InflationIndex> sorted = rows.stream()
                .sorted(Comparator.comparing(InflationIndex::getReferenceMonth))
                .toList();
        InflationIndex oldest = sorted.get(0);
        InflationIndex newest = sorted.get(sorted.size() - 1);
        Optional<InflationIndex> backward = sorted.stream()
                .filter(r -> !r.getReferenceMonth().isAfter(targetMonth))
                .reduce((first, second) -> second);

        when(inflationIndexRepository.findFirstByCurrencyOrderByReferenceMonthAsc(currency)).thenReturn(Optional.of(oldest));
        when(inflationIndexRepository.findFirstByCurrencyOrderByReferenceMonthDesc(currency)).thenReturn(Optional.of(newest));
        when(inflationIndexRepository.findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(currency, targetMonth))
                .thenReturn(backward);
    }

    // --- Cold start ---------------------------------------------------------------------

    @Test
    void coldStart_brl_fetchesNormalizesAndChainsIndexFromBase() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.BRL)).thenReturn(List.of(
                new RawInflationDataPoint(YearMonth.of(2024, 1), new BigDecimal("10")),
                new RawInflationDataPoint(YearMonth.of(2024, 2), new BigDecimal("5")),
                new RawInflationDataPoint(YearMonth.of(2024, 3), new BigDecimal("2"))
        ));
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2024, 3), List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 1), "100"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 2), "105"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 3), "107.1")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 3, 15)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 3));

        // base month: anchored at 100, its own 10% rate is not compounded in
        verifyMonthlyUpsert(InflationCurrency.BRL, YearMonth.of(2024, 1), "100");
        // 100 * 1.05
        verifyMonthlyUpsert(InflationCurrency.BRL, YearMonth.of(2024, 2), "105");
        // 105 * 1.02
        verifyMonthlyUpsert(InflationCurrency.BRL, YearMonth.of(2024, 3), "107.1");
        assertEquals(0, new BigDecimal("107.1").compareTo(result.accumulatedIndex()));
    }

    @Test
    void coldStart_usd_storesRawValueAsIndexWithoutChaining() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.USD)).thenReturn(List.of(
                new RawInflationDataPoint(YearMonth.of(2024, 1), new BigDecimal("300.0")),
                new RawInflationDataPoint(YearMonth.of(2024, 2), new BigDecimal("301.5"))
        ));
        stubLookupQueries(InflationCurrency.USD, YearMonth.of(2024, 2), List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 1), "300.0"),
                row(InflationCurrency.USD, YearMonth.of(2024, 2), "301.5")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 2, 15)), 20);
        service.getInflationIndex(InflationCurrency.USD, YearMonth.of(2024, 2));

        verifyMonthlyUpsert(InflationCurrency.USD, YearMonth.of(2024, 1), "300.0");
        verifyMonthlyUpsert(InflationCurrency.USD, YearMonth.of(2024, 2), "301.5");
    }

    @Test
    void coldStart_upstreamFailure_withNoCache_throwsUnavailable() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.BRL))
                .thenThrow(new InflationDataServiceException(InflationCurrency.BRL, new RuntimeException("network error")));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 20)), 20);

        assertThrows(InflationUnavailableException.class, () ->
                service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 6)));
        verify(inflationIndexRepository, never()).upsert(any(), any(), any(), any());
    }

    // --- Single-month lookup with fallback ----------------------------------------------

    @Test
    void singleMonthLookup_exactMonthAvailable_returnsItDirectly() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(true);
        List<InflationIndex> rows = List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 1), "100"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 2), "105"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 3), "110")
        );
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2024, 2), rows);

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 1, 1)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 2));

        assertEquals(YearMonth.of(2024, 2), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 2), result.returnedMonth());
        assertEquals(0, new BigDecimal("105").compareTo(result.accumulatedIndex()));
        assertFalse(result.oldestAvailable());
        assertFalse(result.newestAvailable());
        verify(dataServiceInflationClient, never()).fetchSeries(any());
    }

    @Test
    void singleMonthLookup_singleMonthGap_walksBackOneMonth() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(true);
        List<InflationIndex> rows = List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 1), "100"),
                // 2024-02 missing
                row(InflationCurrency.BRL, YearMonth.of(2024, 3), "110")
        );
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2024, 2), rows);

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 1, 1)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 2));

        assertEquals(YearMonth.of(2024, 2), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 1), result.returnedMonth());
        assertTrue(result.oldestAvailable());
        assertFalse(result.newestAvailable());
    }

    @Test
    void singleMonthLookup_multiMonthGap_walksBackToNearestAvailable() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(true);
        List<InflationIndex> rows = List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 1), "100"),
                // 2024-02, 2024-03 and 2024-04 missing
                row(InflationCurrency.BRL, YearMonth.of(2024, 5), "120")
        );
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2024, 4), rows);

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 1, 1)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 4));

        assertEquals(YearMonth.of(2024, 1), result.returnedMonth());
        assertTrue(result.oldestAvailable());
    }

    @Test
    void singleMonthLookup_targetNewerThanLatest_returnsNewestWithFlag() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(true);
        List<InflationIndex> rows = List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 1), "100"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 2), "105")
        );
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2024, 6), rows);

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 1, 1)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2024, 6));

        assertEquals(YearMonth.of(2024, 2), result.returnedMonth());
        assertTrue(result.newestAvailable());
        assertFalse(result.oldestAvailable());
    }

    @Test
    void singleMonthLookup_targetOlderThanOldest_returnsOldestWithFlag() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.BRL)).thenReturn(true);
        List<InflationIndex> rows = List.of(
                row(InflationCurrency.BRL, YearMonth.of(2024, 3), "100"),
                row(InflationCurrency.BRL, YearMonth.of(2024, 4), "102")
        );
        stubLookupQueries(InflationCurrency.BRL, YearMonth.of(2020, 1), rows);

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 1, 1)), 20);
        InflationLookupResultDTO result = service.getInflationIndex(InflationCurrency.BRL, YearMonth.of(2020, 1));

        assertEquals(YearMonth.of(2020, 1), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 3), result.returnedMonth());
        assertTrue(result.oldestAvailable());
        assertFalse(result.newestAvailable());
    }

    // --- Lazy monthly refresh ------------------------------------------------------------

    @Test
    void refresh_firesOnConfiguredDay_whenCurrentMonthMissing() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, currentMonth)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.USD)).thenReturn(List.of(
                new RawInflationDataPoint(YearMonth.of(2024, 6), new BigDecimal("300")),
                new RawInflationDataPoint(currentMonth, new BigDecimal("301"))
        ));
        stubLookupQueries(InflationCurrency.USD, currentMonth, List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 6), "300"),
                row(InflationCurrency.USD, currentMonth, "301")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 20)), 20);
        service.getInflationIndex(InflationCurrency.USD, currentMonth);

        verify(dataServiceInflationClient).fetchSeries(InflationCurrency.USD);
        verifyMonthlyUpsert(InflationCurrency.USD, YearMonth.of(2024, 6), "300");
        verifyMonthlyUpsert(InflationCurrency.USD, currentMonth, "301");
    }

    @Test
    void refresh_firesAfterConfiguredDay_whenCurrentMonthMissing() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, currentMonth)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.USD)).thenReturn(List.of(
                new RawInflationDataPoint(YearMonth.of(2024, 6), new BigDecimal("300")),
                new RawInflationDataPoint(currentMonth, new BigDecimal("301"))
        ));
        stubLookupQueries(InflationCurrency.USD, currentMonth, List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 6), "300"),
                row(InflationCurrency.USD, currentMonth, "301")
        ));

        // day 25 is after the threshold day of 20 - confirms ">=" semantics, not just "=="
        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 25)), 20);
        service.getInflationIndex(InflationCurrency.USD, currentMonth);

        verify(dataServiceInflationClient).fetchSeries(InflationCurrency.USD);
    }

    @Test
    void refresh_doesNotFireBeforeConfiguredDay() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        List<InflationIndex> rows = List.of(row(InflationCurrency.USD, YearMonth.of(2024, 6), "300"));
        stubLookupQueries(InflationCurrency.USD, YearMonth.of(2024, 6), rows);

        // day 19 < threshold 20
        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 19)), 20);
        service.getInflationIndex(InflationCurrency.USD, YearMonth.of(2024, 6));

        verify(dataServiceInflationClient, never()).fetchSeries(any());
        verify(inflationIndexRepository, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void refresh_doesNotFireWhenCurrentMonthAlreadyPresent() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, currentMonth)).thenReturn(true);
        stubLookupQueries(InflationCurrency.USD, currentMonth, List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 6), "300"),
                row(InflationCurrency.USD, currentMonth, "301")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 25)), 20);
        service.getInflationIndex(InflationCurrency.USD, currentMonth);

        verify(dataServiceInflationClient, never()).fetchSeries(any());
    }

    @Test
    void refresh_upsertsAfterSimulatedUpstreamRevision() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, currentMonth)).thenReturn(false);

        when(dataServiceInflationClient.fetchSeries(InflationCurrency.USD)).thenReturn(List.of(
                new RawInflationDataPoint(YearMonth.of(2024, 3), new BigDecimal("299.5")), // BLS/FRED revised this month
                new RawInflationDataPoint(currentMonth, new BigDecimal("301.0"))
        ));
        stubLookupQueries(InflationCurrency.USD, currentMonth, List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 3), "299.5"),
                row(InflationCurrency.USD, currentMonth, "301.0")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 20)), 20);
        service.getInflationIndex(InflationCurrency.USD, currentMonth);

        // exactly one upsert call per month - the revised March value overwrites the stale
        // one atomically (ON CONFLICT DO UPDATE) rather than inserting a duplicate row
        verifyMonthlyUpsert(InflationCurrency.USD, YearMonth.of(2024, 3), "299.5");
        verifyMonthlyUpsert(InflationCurrency.USD, currentMonth, "301.0");
        verify(inflationIndexRepository, times(2)).upsert(any(UUID.class), eq("USD"), any(LocalDate.class), any());
    }

    @Test
    void refresh_upstreamFailure_withExistingCache_stillServesCachedData() {
        when(inflationIndexRepository.existsByCurrency(InflationCurrency.USD)).thenReturn(true);
        YearMonth currentMonth = YearMonth.of(2024, 7);
        when(inflationIndexRepository.existsByCurrencyAndReferenceMonth(InflationCurrency.USD, currentMonth)).thenReturn(false);
        when(dataServiceInflationClient.fetchSeries(InflationCurrency.USD))
                .thenThrow(new InflationDataServiceException(InflationCurrency.USD, new RuntimeException("network error")));
        stubLookupQueries(InflationCurrency.USD, YearMonth.of(2024, 6), List.of(
                row(InflationCurrency.USD, YearMonth.of(2024, 6), "300")
        ));

        InflationCacheService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 20)), 20);

        InflationLookupResultDTO result = assertDoesNotThrow(() ->
                service.getInflationIndex(InflationCurrency.USD, YearMonth.of(2024, 6)));

        assertEquals(YearMonth.of(2024, 6), result.returnedMonth());
        verify(inflationIndexRepository, never()).upsert(any(), any(), any(), any());
    }
}
