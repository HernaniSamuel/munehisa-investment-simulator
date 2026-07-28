package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.dto.InflationDeflationResultDTO;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.exceptions.FutureDeflationTargetException;
import com.munehisa.backend.exceptions.UnsupportedDeflationCurrencyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class InflationDeflationService {
    private static final MathContext MATH_CONTEXT = new MathContext(50);

    private final InflationCacheService inflationCacheService;
    private final Clock clock;

    /**
     * Converts a value expressed in today's purchasing power into the nominal amount
     * equivalent at targetMonth, by dividing out the accumulated inflation between
     * targetMonth and the real current calendar month - a ratio of two accumulated-index
     * points, not a sum of monthly rates, since the index already encodes compounding.
     */
    public InflationDeflationResultDTO deflate(BigDecimal todayValue, String baseCurrencyRaw, YearMonth targetMonth) {
        InflationCurrency currency = resolveCurrency(baseCurrencyRaw);

        YearMonth currentMonth = YearMonth.now(clock);
        if (targetMonth.isAfter(currentMonth)) {
            throw new FutureDeflationTargetException(targetMonth, currentMonth);
        }

        InflationLookupResultDTO currentMonthLookup = inflationCacheService.getInflationIndex(currency, currentMonth);
        InflationLookupResultDTO targetMonthLookup = inflationCacheService.getInflationIndex(currency, targetMonth);

        BigDecimal ratio = targetMonthLookup.accumulatedIndex()
                .divide(currentMonthLookup.accumulatedIndex(), MATH_CONTEXT);
        BigDecimal deflatedValue = todayValue.multiply(ratio, MATH_CONTEXT);

        return new InflationDeflationResultDTO(todayValue, deflatedValue, currency, currentMonthLookup, targetMonthLookup);
    }

    private InflationCurrency resolveCurrency(String baseCurrencyRaw) {
        if (baseCurrencyRaw == null) {
            throw new UnsupportedDeflationCurrencyException(null);
        }
        try {
            return InflationCurrency.valueOf(baseCurrencyRaw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedDeflationCurrencyException(baseCurrencyRaw);
        }
    }
}
