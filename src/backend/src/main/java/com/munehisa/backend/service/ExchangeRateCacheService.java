package com.munehisa.backend.service;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
import com.munehisa.backend.dto.ExchangeRateLookupResultDTO;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.exceptions.ExchangeRateDataServiceException;
import com.munehisa.backend.exceptions.ExchangeRateUnavailableException;
import com.munehisa.backend.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateCacheService {
    private static final MathContext MATH_CONTEXT = new MathContext(50);
    private static final BigDecimal ONE = BigDecimal.ONE;
    // Same fixed sentinel date the data-service uses for its own from==to synthetic
    // series (yfinance_exchange_client.py's _SAME_CURRENCY_DATE) - it never changes
    // and needs no refresh, so it's never persisted here either.
    private static final YearMonth SAME_CURRENCY_MONTH = YearMonth.of(1970, 1);

    private final ExchangeRateRepository exchangeRateRepository;
    private final DataServiceExchangeRateClient dataServiceExchangeRateClient;

    public ExchangeRateLookupResultDTO getExchangeRate(String fromCurrencyRaw, String toCurrencyRaw, YearMonth targetMonth) {
        String fromCurrency = fromCurrencyRaw.toUpperCase();
        String toCurrency = toCurrencyRaw.toUpperCase();

        if (fromCurrency.equals(toCurrency)) {
            return new ExchangeRateLookupResultDTO(
                    fromCurrency, toCurrency, targetMonth, SAME_CURRENCY_MONTH,
                    ONE, ONE, ONE, ONE, true, true);
        }

        boolean requestedIsCanonical = fromCurrency.compareTo(toCurrency) < 0;
        String base = requestedIsCanonical ? fromCurrency : toCurrency;
        String quote = requestedIsCanonical ? toCurrency : fromCurrency;

        ensureFreshData(base, quote, targetMonth);

        ExchangeRate oldest = exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthAsc(base, quote)
                .orElseThrow(() -> new IllegalStateException(
                        "No exchange rate data available for " + base + "/" + quote + " after a successful refresh"));
        ExchangeRate newest = exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc(base, quote)
                .orElseThrow();
        ExchangeRate resolved = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(base, quote, targetMonth)
                .orElse(oldest);

        YearMonth returnedMonth = resolved.getReferenceMonth();
        boolean oldestAvailable = returnedMonth.equals(oldest.getReferenceMonth());
        boolean newestAvailable = returnedMonth.equals(newest.getReferenceMonth());

        return toDto(fromCurrency, toCurrency, targetMonth, resolved, oldestAvailable, newestAvailable, !requestedIsCanonical);
    }

    private ExchangeRateLookupResultDTO toDto(
            String fromCurrency, String toCurrency, YearMonth requestedMonth, ExchangeRate resolved,
            boolean oldestAvailable, boolean newestAvailable, boolean inverted
    ) {
        BigDecimal open = resolved.getOpen();
        BigDecimal high = resolved.getHigh();
        BigDecimal low = resolved.getLow();
        BigDecimal close = resolved.getClose();

        if (inverted) {
            // High and low swap under inversion, not just invert in place: if low < high
            // then 1/low > 1/high, so the inverted series' high is 1/low and its low is
            // 1/high. Division is done in BigDecimal space, mirroring the exact bug class
            // already fixed once in the data-service (fix(data): invert exchange rates in
            // Decimal space, not float64).
            BigDecimal invertedOpen = ONE.divide(open, MATH_CONTEXT);
            BigDecimal invertedHigh = ONE.divide(low, MATH_CONTEXT);
            BigDecimal invertedLow = ONE.divide(high, MATH_CONTEXT);
            BigDecimal invertedClose = ONE.divide(close, MATH_CONTEXT);
            open = invertedOpen;
            high = invertedHigh;
            low = invertedLow;
            close = invertedClose;
        }

        return new ExchangeRateLookupResultDTO(
                fromCurrency, toCurrency, requestedMonth, resolved.getReferenceMonth(),
                open, high, low, close, oldestAvailable, newestAvailable);
    }

    private void ensureFreshData(String base, String quote, YearMonth targetMonth) {
        boolean hasAnyData = exchangeRateRepository.existsByBaseCurrencyAndQuoteCurrency(base, quote);

        if (!hasAnyData) {
            try {
                refetchAndUpsert(base, quote);
            } catch (ExchangeRateDataServiceException exception) {
                throw new ExchangeRateUnavailableException(base, quote, exception);
            }
            return;
        }

        YearMonth latestCachedMonth = exchangeRateRepository
                .findFirstByBaseCurrencyAndQuoteCurrencyOrderByReferenceMonthDesc(base, quote)
                .orElseThrow()
                .getReferenceMonth();

        if (targetMonth.isAfter(latestCachedMonth)) {
            try {
                refetchAndUpsert(base, quote);
            } catch (ExchangeRateDataServiceException exception) {
                log.warn("Exchange rate refresh failed for {}/{}, serving cached data", base, quote, exception);
            }
        }
    }

    private void refetchAndUpsert(String base, String quote) {
        List<RawExchangeMonthDataPoint> raw = dataServiceExchangeRateClient.fetchSeries(base, quote);
        upsert(base, quote, raw);
    }

    private void upsert(String base, String quote, List<RawExchangeMonthDataPoint> raw) {
        Map<YearMonth, ExchangeRate> existing = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote).stream()
                .collect(Collectors.toMap(ExchangeRate::getReferenceMonth, Function.identity()));

        List<ExchangeRate> toSave = raw.stream()
                .map(point -> {
                    ExchangeRate row = existing.getOrDefault(point.month(), new ExchangeRate());
                    row.setBaseCurrency(base);
                    row.setQuoteCurrency(quote);
                    row.setReferenceMonth(point.month());
                    row.setOpen(point.open());
                    row.setHigh(point.high());
                    row.setLow(point.low());
                    row.setClose(point.close());
                    return row;
                })
                .toList();

        exchangeRateRepository.saveAll(toSave);
    }
}
