package com.munehisa.backend.service;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
import com.munehisa.backend.dto.ExchangeRateLookupResultDTO;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.exceptions.ExchangeRateDataServiceException;
import com.munehisa.backend.exceptions.ExchangeRateUnavailableException;
import com.munehisa.backend.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Repository derived-query behavior (backward-walk, oldest/newest, unique/CHECK
 * constraints) is proven against a real Postgres instance in
 * {@code ExchangeRateRepositoryTest}; here the repository is mocked to simulate that
 * already-proven behavior, so this suite can focus on ExchangeRateCacheService's own
 * logic: canonical-direction resolution and BigDecimal inversion, cold-start/on-demand
 * refresh gating, upsert-without-duplication, and the lookup-fallback flags.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateCacheServiceTest {

    private static final MathContext MATH_CONTEXT = new MathContext(50);

    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private DataServiceExchangeRateClient dataServiceExchangeRateClient;

    private ExchangeRateCacheService buildService() {
        return new ExchangeRateCacheService(exchangeRateRepository, dataServiceExchangeRateClient);
    }

    private ExchangeRate row(String base, String quote, YearMonth month, String open, String high, String low, String close) {
        ExchangeRate entry = new ExchangeRate();
        entry.setBaseCurrency(base);
        entry.setQuoteCurrency(quote);
        entry.setReferenceMonth(month);
        entry.setOpen(new BigDecimal(open));
        entry.setHigh(new BigDecimal(high));
        entry.setLow(new BigDecimal(low));
        entry.setClose(new BigDecimal(close));
        return entry;
    }

    private static BigDecimal inverse(String value) {
        return BigDecimal.ONE.divide(new BigDecimal(value), MATH_CONTEXT);
    }

    // Confirms a single upsert() call for the given pair/month with the given OHLC - the
    // atomic-upsert equivalent of the old assertSame(stale, updated) "same entity, mutated in
    // place" check, since there's no entity to mutate anymore.
    private void verifyMonthlyUpsert(String base, String quote, YearMonth month, String open, String high, String low, String close) {
        verify(exchangeRateRepository).upsert(
                any(UUID.class), eq(base), eq(quote), eq(month.atDay(1)),
                eq(new BigDecimal(open)), eq(new BigDecimal(high)), eq(new BigDecimal(low)), eq(new BigDecimal(close)));
    }

    /** Stubs the three lookup queries against an in-memory row set, mirroring what the
     *  real derived queries (proven in ExchangeRateRepositoryTest) would return for that
     *  data. Only valid for tests where no refresh fires - the cached state doesn't
     *  change between ensureFreshData's check and getExchangeRate's boundary queries. */
    private void stubLookupQueries(String base, String quote, YearMonth targetMonth, List<ExchangeRate> rows) {
        List<ExchangeRate> sorted = rows.stream()
                .sorted(Comparator.comparing(ExchangeRate::getReferenceMonth))
                .toList();
        ExchangeRate oldest = sorted.get(0);
        ExchangeRate newest = sorted.get(sorted.size() - 1);
        Optional<ExchangeRate> backward = sorted.stream()
                .filter(r -> !r.getReferenceMonth().isAfter(targetMonth))
                .reduce((first, second) -> second);

        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc(base, quote)).thenReturn(Optional.of(oldest));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc(base, quote)).thenReturn(Optional.of(newest));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(base, quote, targetMonth))
                .thenReturn(backward);
    }

    // --- Cold start ---------------------------------------------------------------------

    @Test
    void coldStart_fetchesAndPersistsFullHistoryForCanonicalPair() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(false);
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(List.of(
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 1), new BigDecimal("0.20"), new BigDecimal("0.21"), new BigDecimal("0.19"), new BigDecimal("0.205")),
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 2), new BigDecimal("0.205"), new BigDecimal("0.22"), new BigDecimal("0.20"), new BigDecimal("0.215"))
        ));
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 2), List.of(
                row("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.21", "0.19", "0.205"),
                row("BRL", "USD", YearMonth.of(2024, 2), "0.205", "0.22", "0.20", "0.215")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 2));

        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.21", "0.19", "0.205");
        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 2), "0.205", "0.22", "0.20", "0.215");
        assertEquals(YearMonth.of(2024, 2), result.returnedMonth());
        assertEquals(0, new BigDecimal("0.215").compareTo(result.close()));
    }

    @Test
    void coldStart_upstreamFailure_withNoCache_throwsUnavailable() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(false);
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD"))
                .thenThrow(new ExchangeRateDataServiceException("BRL", "USD", new RuntimeException("network error")));

        ExchangeRateCacheService service = buildService();

        assertThrows(ExchangeRateUnavailableException.class, () ->
                service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 6)));
        verify(exchangeRateRepository, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Canonical vs non-canonical direction --------------------------------------------

    @Test
    void canonicalDirectionLookup_returnsRowValuesDirectlyWithoutInversion() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 1), List.of(
                row("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.22", "0.18", "0.21")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 1));

        assertEquals(0, new BigDecimal("0.20").compareTo(result.open()));
        assertEquals(0, new BigDecimal("0.22").compareTo(result.high()));
        assertEquals(0, new BigDecimal("0.18").compareTo(result.low()));
        assertEquals(0, new BigDecimal("0.21").compareTo(result.close()));
        assertEquals("BRL", result.fromCurrency());
        assertEquals("USD", result.toCurrency());
        verify(dataServiceExchangeRateClient, never()).fetchSeries(anyString(), anyString());
    }

    @Test
    void nonCanonicalDirectionLookup_invertsOhlcInBigDecimalWithHighLowSwap() {
        // canonical pair is BRL/USD; requesting USD -> BRL (the reverse direction) must
        // serve the same cached row inverted, not fetch/cache a second series.
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        // low (0.18) < high (0.22): after inversion, 1/0.18 > 1/0.22, so the inverted
        // series' high must come from the direct series' low, and vice versa.
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 1), List.of(
                row("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.22", "0.18", "0.21")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("USD", "BRL", YearMonth.of(2024, 1));

        assertEquals(0, inverse("0.20").compareTo(result.open()));
        assertEquals(0, inverse("0.18").compareTo(result.high())); // inverted low -> high
        assertEquals(0, inverse("0.22").compareTo(result.low()));  // inverted high -> low
        assertEquals(0, inverse("0.21").compareTo(result.close()));
        assertTrue(result.high().compareTo(result.low()) > 0); // still a valid OHLC row after the swap
        assertEquals("USD", result.fromCurrency());
        assertEquals("BRL", result.toCurrency());
        // no second fetch/cache for the reverse direction - the same canonical pair is queried
        verify(dataServiceExchangeRateClient, never()).fetchSeries(anyString(), anyString());
        verify(exchangeRateRepository, never()).existsByBaseCurrencyAndQuoteCurrency("USD", "BRL");
    }

    // --- Same-currency shortcut ------------------------------------------------------------

    @Test
    void sameCurrency_returnsSentinelSeriesWithoutTouchingRepositoryOrClient() {
        ExchangeRateCacheService service = buildService();

        ExchangeRateLookupResultDTO result = service.getExchangeRate("usd", "USD", YearMonth.of(2024, 6));

        assertEquals("USD", result.fromCurrency());
        assertEquals("USD", result.toCurrency());
        assertEquals(0, BigDecimal.ONE.compareTo(result.open()));
        assertEquals(0, BigDecimal.ONE.compareTo(result.high()));
        assertEquals(0, BigDecimal.ONE.compareTo(result.low()));
        assertEquals(0, BigDecimal.ONE.compareTo(result.close()));
        assertTrue(result.oldestAvailable());
        assertTrue(result.newestAvailable());
        verifyNoInteractions(exchangeRateRepository, dataServiceExchangeRateClient);
    }

    // --- Single-month lookup with fallback ----------------------------------------------

    @Test
    void singleMonthLookup_singleMonthGap_walksBackOneMonth() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 2), List.of(
                row("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.21", "0.19", "0.20"),
                // 2024-02 missing
                row("BRL", "USD", YearMonth.of(2024, 3), "0.22", "0.23", "0.21", "0.22")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 2));

        assertEquals(YearMonth.of(2024, 2), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 1), result.returnedMonth());
        assertTrue(result.oldestAvailable());
        assertFalse(result.newestAvailable());
    }

    @Test
    void singleMonthLookup_multiMonthGap_walksBackToNearestAvailable() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 4), List.of(
                row("BRL", "USD", YearMonth.of(2024, 1), "0.20", "0.21", "0.19", "0.20"),
                // 2024-02, 2024-03, 2024-04 missing
                row("BRL", "USD", YearMonth.of(2024, 5), "0.25", "0.26", "0.24", "0.25")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 4));

        assertEquals(YearMonth.of(2024, 1), result.returnedMonth());
        assertTrue(result.oldestAvailable());
    }

    // --- On-demand refresh -----------------------------------------------------------------

    @Test
    void refresh_firesWhenTargetMonthNewerThanLatestIngested() {
        ExchangeRate juneRow = row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20");
        ExchangeRate julyRow = row("BRL", "USD", YearMonth.of(2024, 7), "0.21", "0.22", "0.20", "0.215");

        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        // First call is ensureFreshData's trigger check (sees only June cached, so
        // target=July fires a refresh); second call is getExchangeRate's "newest"
        // boundary query afterward, which sees July as the new latest month.
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc("BRL", "USD"))
                .thenReturn(Optional.of(juneRow), Optional.of(julyRow));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc("BRL", "USD"))
                .thenReturn(Optional.of(juneRow));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                "BRL", "USD", YearMonth.of(2024, 7))).thenReturn(Optional.of(julyRow));
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(List.of(
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 6), new BigDecimal("0.20"), new BigDecimal("0.21"), new BigDecimal("0.19"), new BigDecimal("0.20")),
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 7), new BigDecimal("0.21"), new BigDecimal("0.22"), new BigDecimal("0.20"), new BigDecimal("0.215"))
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 7));

        verify(dataServiceExchangeRateClient).fetchSeries("BRL", "USD");
        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20");
        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 7), "0.21", "0.22", "0.20", "0.215");
        assertEquals(YearMonth.of(2024, 7), result.returnedMonth());
        assertTrue(result.newestAvailable());
    }

    @Test
    void refresh_doesNotFireWhenTargetMonthOlderThanOldestIngested() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        stubLookupQueries("BRL", "USD", YearMonth.of(2020, 1), List.of(
                row("BRL", "USD", YearMonth.of(2024, 3), "0.19", "0.20", "0.18", "0.195"),
                row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20")
        ));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = service.getExchangeRate("BRL", "USD", YearMonth.of(2020, 1));

        assertEquals(YearMonth.of(2020, 1), result.requestedMonth());
        assertEquals(YearMonth.of(2024, 3), result.returnedMonth());
        assertTrue(result.oldestAvailable());
        assertFalse(result.newestAvailable());
        verify(dataServiceExchangeRateClient, never()).fetchSeries(anyString(), anyString());
    }

    @Test
    void refresh_doesNotFireWhenRequestedMonthUnchanged() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        stubLookupQueries("BRL", "USD", YearMonth.of(2024, 6), List.of(
                row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20")
        ));

        ExchangeRateCacheService service = buildService();
        service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 6));

        verify(dataServiceExchangeRateClient, never()).fetchSeries(anyString(), anyString());
        verify(exchangeRateRepository, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refresh_upsertsAfterSimulatedUpstreamRevision_withoutDuplicatingExistingRow() {
        ExchangeRate staleJuneRow = row("BRL", "USD", YearMonth.of(2024, 6), "0.199", "0.209", "0.189", "0.199"); // pre-revision
        ExchangeRate revisedJuneRow = row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20");
        ExchangeRate julyRow = row("BRL", "USD", YearMonth.of(2024, 7), "0.21", "0.22", "0.20", "0.215");

        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc("BRL", "USD"))
                .thenReturn(Optional.of(staleJuneRow), Optional.of(julyRow));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc("BRL", "USD"))
                .thenReturn(Optional.of(revisedJuneRow));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                "BRL", "USD", YearMonth.of(2024, 7))).thenReturn(Optional.of(julyRow));
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD")).thenReturn(List.of(
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 6), new BigDecimal("0.20"), new BigDecimal("0.21"), new BigDecimal("0.19"), new BigDecimal("0.20")),
                new RawExchangeMonthDataPoint(YearMonth.of(2024, 7), new BigDecimal("0.21"), new BigDecimal("0.22"), new BigDecimal("0.20"), new BigDecimal("0.215"))
        ));

        ExchangeRateCacheService service = buildService();
        service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 7));

        // exactly one upsert call per month - the revised June value overwrites the stale
        // one atomically (ON CONFLICT DO UPDATE) rather than inserting a duplicate row
        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20");
        verifyMonthlyUpsert("BRL", "USD", YearMonth.of(2024, 7), "0.21", "0.22", "0.20", "0.215");
        verify(exchangeRateRepository, times(2)).upsert(
                any(UUID.class), eq("BRL"), eq("USD"), any(LocalDate.class), any(), any(), any(), any());
    }

    @Test
    void refresh_upstreamFailure_withExistingCache_stillServesCachedData() {
        when(exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency("BRL", "USD")).thenReturn(true);
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc("BRL", "USD"))
                .thenReturn(Optional.of(row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20")));
        when(dataServiceExchangeRateClient.fetchSeries("BRL", "USD"))
                .thenThrow(new ExchangeRateDataServiceException("BRL", "USD", new RuntimeException("network error")));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc("BRL", "USD"))
                .thenReturn(Optional.of(row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20")));
        when(exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(
                "BRL", "USD", YearMonth.of(2024, 7)))
                .thenReturn(Optional.of(row("BRL", "USD", YearMonth.of(2024, 6), "0.20", "0.21", "0.19", "0.20")));

        ExchangeRateCacheService service = buildService();
        ExchangeRateLookupResultDTO result = assertDoesNotThrow(() ->
                service.getExchangeRate("BRL", "USD", YearMonth.of(2024, 7)));

        assertEquals(YearMonth.of(2024, 6), result.returnedMonth());
        verify(exchangeRateRepository, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
