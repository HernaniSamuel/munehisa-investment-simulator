package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.exceptions.FutureDeflationTargetException;
import com.munehisa.backend.exceptions.UnsupportedDeflationCurrencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * InflationCacheService's own lookup/fallback behavior is proven in
 * InflationCacheServiceTest; here it is mocked so this suite can focus on
 * InflationDeflationService's own logic: the ratio math, the future-target guard, the
 * currency allowlist, and passing the two underlying lookups' fallback flags through
 * untouched.
 */
@ExtendWith(MockitoExtension.class)
class InflationDeflationServiceTest {

    @Mock
    private InflationCacheService inflationCacheService;

    private InflationDeflationService buildService(Clock clock) {
        return new InflationDeflationService(inflationCacheService, clock);
    }

    private static Clock fixedClockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private InflationLookupResultDTO lookup(
            YearMonth requestedMonth, YearMonth returnedMonth, String accumulatedIndex,
            boolean oldestAvailable, boolean newestAvailable
    ) {
        return new InflationLookupResultDTO(
                InflationCurrency.BRL, requestedMonth, returnedMonth,
                new BigDecimal(accumulatedIndex), oldestAvailable, newestAvailable);
    }

    // --- Ratio math -----------------------------------------------------------------------

    @Test
    void deflate_exactMonthsBothEnds_appliesIndexRatio() {
        YearMonth currentMonth = YearMonth.of(2024, 7);
        YearMonth targetMonth = YearMonth.of(2020, 1);

        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, currentMonth))
                .thenReturn(lookup(currentMonth, currentMonth, "200", false, true));
        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, targetMonth))
                .thenReturn(lookup(targetMonth, targetMonth, "100", true, false));

        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        InflationDeflationResultDTO result = service.deflate(new BigDecimal("1000"), "BRL", targetMonth);

        // 1000 * (100 / 200) = 500
        assertEquals(0, new BigDecimal("500").compareTo(result.deflatedValue()));
        assertEquals(0, new BigDecimal("1000").compareTo(result.originalValue()));
        assertEquals(InflationCurrency.BRL, result.currency());
        assertEquals(currentMonth, result.currentMonthLookup().returnedMonth());
        assertEquals(targetMonth, result.targetMonthLookup().returnedMonth());
    }

    @Test
    void deflate_targetEqualsCurrentMonth_ratioOfOne() {
        YearMonth month = YearMonth.of(2024, 7);

        when(inflationCacheService.getInflationIndex(InflationCurrency.USD, month))
                .thenReturn(lookup(month, month, "312.5", false, true));

        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        InflationDeflationResultDTO result = service.deflate(new BigDecimal("750.00"), "USD", month);

        assertEquals(0, new BigDecimal("750.00").compareTo(result.deflatedValue()));
        verify(inflationCacheService, times(2)).getInflationIndex(InflationCurrency.USD, month);
    }

    // --- Fallback flag pass-through --------------------------------------------------------

    @Test
    void deflate_targetMonthFallsBack_propagatesFallbackFlags() {
        YearMonth currentMonth = YearMonth.of(2024, 7);
        YearMonth requestedTargetMonth = YearMonth.of(2018, 4);
        YearMonth substitutedTargetMonth = YearMonth.of(2019, 1);

        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, currentMonth))
                .thenReturn(lookup(currentMonth, currentMonth, "200", false, true));
        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, requestedTargetMonth))
                .thenReturn(lookup(requestedTargetMonth, substitutedTargetMonth, "100", true, false));

        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        InflationDeflationResultDTO result = service.deflate(new BigDecimal("1000"), "BRL", requestedTargetMonth);

        assertEquals(requestedTargetMonth, result.targetMonthLookup().requestedMonth());
        assertEquals(substitutedTargetMonth, result.targetMonthLookup().returnedMonth());
        assertTrue(result.targetMonthLookup().oldestAvailable());
        assertFalse(result.targetMonthLookup().newestAvailable());
        assertFalse(result.currentMonthLookup().oldestAvailable());
        assertTrue(result.currentMonthLookup().newestAvailable());
    }

    @Test
    void deflate_bothMonthsFallBack_propagatesBothFallbackFlags() {
        YearMonth requestedCurrentMonth = YearMonth.of(2024, 7);
        YearMonth substitutedCurrentMonth = YearMonth.of(2024, 5);
        YearMonth requestedTargetMonth = YearMonth.of(2018, 4);
        YearMonth substitutedTargetMonth = YearMonth.of(2019, 1);

        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, requestedCurrentMonth))
                .thenReturn(lookup(requestedCurrentMonth, substitutedCurrentMonth, "180", false, true));
        when(inflationCacheService.getInflationIndex(InflationCurrency.BRL, requestedTargetMonth))
                .thenReturn(lookup(requestedTargetMonth, substitutedTargetMonth, "100", true, false));

        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));
        InflationDeflationResultDTO result = service.deflate(new BigDecimal("900"), "BRL", requestedTargetMonth);

        assertEquals(substitutedCurrentMonth, result.currentMonthLookup().returnedMonth());
        assertTrue(result.currentMonthLookup().newestAvailable());
        assertEquals(substitutedTargetMonth, result.targetMonthLookup().returnedMonth());
        assertTrue(result.targetMonthLookup().oldestAvailable());
        // 900 * (100 / 180) = 500
        assertEquals(0, new BigDecimal("500").compareTo(result.deflatedValue()));
    }

    // --- Validation -------------------------------------------------------------------------

    @Test
    void deflate_unsupportedCurrency_throwsUnsupportedDeflationCurrencyException() {
        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));

        assertThrows(UnsupportedDeflationCurrencyException.class, () ->
                service.deflate(new BigDecimal("100"), "EUR", YearMonth.of(2024, 1)));
        verifyNoInteractions(inflationCacheService);
    }

    @Test
    void deflate_nullCurrency_throwsUnsupportedDeflationCurrencyException() {
        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));

        assertThrows(UnsupportedDeflationCurrencyException.class, () ->
                service.deflate(new BigDecimal("100"), null, YearMonth.of(2024, 1)));
        verifyNoInteractions(inflationCacheService);
    }

    @Test
    void deflate_targetMonthAfterCurrentMonth_throwsFutureDeflationTargetException() {
        InflationDeflationService service = buildService(fixedClockOn(LocalDate.of(2024, 7, 15)));

        assertThrows(FutureDeflationTargetException.class, () ->
                service.deflate(new BigDecimal("100"), "BRL", YearMonth.of(2024, 8)));
        verifyNoInteractions(inflationCacheService);
    }
}
