package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.domain.inflation.InflationIndex;
import com.munehisa.backend.dto.InflationLookupResultDTO;
import com.munehisa.backend.dto.dataservice.RawInflationDataPoint;
import com.munehisa.backend.exceptions.InflationDataServiceException;
import com.munehisa.backend.exceptions.InflationUnavailableException;
import com.munehisa.backend.repository.InflationIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InflationCacheService {
    private static final BigDecimal BASE_INDEX = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MATH_CONTEXT = new MathContext(50);

    private final InflationIndexRepository inflationIndexRepository;
    private final DataServiceInflationClient dataServiceInflationClient;
    private final Clock clock;

    @Value("${inflation.refresh.threshold-day}")
    private int refreshThresholdDay;

    public InflationLookupResultDTO getInflationIndex(InflationCurrency currency, YearMonth targetMonth) {
        ensureFreshData(currency);

        InflationIndex oldest = inflationIndexRepository.findFirstByCurrencyOrderByReferenceMonthAsc(currency)
                .orElseThrow(() -> new IllegalStateException(
                        "No inflation data available for " + currency + " after a successful refresh"));
        InflationIndex newest = inflationIndexRepository.findFirstByCurrencyOrderByReferenceMonthDesc(currency)
                .orElseThrow();

        InflationIndex resolved = inflationIndexRepository
                .findFirstByCurrencyAndReferenceMonthLessThanEqualOrderByReferenceMonthDesc(currency, targetMonth)
                .orElse(oldest);

        YearMonth returnedMonth = resolved.getReferenceMonth();

        return new InflationLookupResultDTO(
                currency,
                targetMonth,
                returnedMonth,
                resolved.getAccumulatedIndex(),
                returnedMonth.equals(oldest.getReferenceMonth()),
                returnedMonth.equals(newest.getReferenceMonth())
        );
    }

    private void ensureFreshData(InflationCurrency currency) {
        boolean hasAnyData = inflationIndexRepository.existsByCurrency(currency);

        if (!hasAnyData) {
            try {
                refetchAndUpsert(currency);
            } catch (InflationDataServiceException exception) {
                throw new InflationUnavailableException(currency, exception);
            }
            return;
        }

        YearMonth currentMonth = YearMonth.now(clock);
        boolean pastThresholdDay = LocalDate.now(clock).getDayOfMonth() >= refreshThresholdDay;
        boolean currentMonthMissing = !inflationIndexRepository.existsByCurrencyAndReferenceMonth(currency, currentMonth);

        if (pastThresholdDay && currentMonthMissing) {
            try {
                refetchAndUpsert(currency);
            } catch (InflationDataServiceException exception) {
                log.warn("Inflation refresh failed for {}, serving cached data", currency, exception);
            }
        }
    }

    private void refetchAndUpsert(InflationCurrency currency) {
        List<RawInflationDataPoint> raw = dataServiceInflationClient.fetchSeries(currency);
        List<NormalizedMonth> normalized = normalize(currency, raw);
        upsert(currency, normalized);
    }

    private List<NormalizedMonth> normalize(InflationCurrency currency, List<RawInflationDataPoint> raw) {
        List<RawInflationDataPoint> sorted = raw.stream()
                .sorted(Comparator.comparing(RawInflationDataPoint::month))
                .toList();

        if (currency == InflationCurrency.USD) {
            return sorted.stream()
                    .map(point -> new NormalizedMonth(point.month(), point.rawValue()))
                    .toList();
        }

        // BRL/IPCA is a monthly rate: chain into an accumulated index anchored at BASE_INDEX
        // on the earliest month. That earliest month's own rate is not compounded in - there
        // is no prior month for it to apply to, the base is simply anchored there.
        List<NormalizedMonth> chained = new ArrayList<>(sorted.size());
        BigDecimal runningIndex = BASE_INDEX;
        for (int i = 0; i < sorted.size(); i++) {
            RawInflationDataPoint point = sorted.get(i);
            if (i > 0) {
                BigDecimal rateFraction = point.rawValue().divide(HUNDRED, MATH_CONTEXT);
                runningIndex = runningIndex.multiply(BigDecimal.ONE.add(rateFraction, MATH_CONTEXT), MATH_CONTEXT);
            }
            chained.add(new NormalizedMonth(point.month(), runningIndex));
        }
        return chained;
    }

    private void upsert(InflationCurrency currency, List<NormalizedMonth> normalized) {
        Map<YearMonth, InflationIndex> existing = inflationIndexRepository.findByCurrency(currency).stream()
                .collect(Collectors.toMap(InflationIndex::getReferenceMonth, Function.identity()));

        List<InflationIndex> toSave = normalized.stream()
                .map(month -> {
                    InflationIndex row = existing.getOrDefault(month.month(), new InflationIndex());
                    row.setCurrency(currency);
                    row.setReferenceMonth(month.month());
                    row.setAccumulatedIndex(month.accumulatedIndex());
                    return row;
                })
                .toList();

        inflationIndexRepository.saveAll(toSave);
    }

    private record NormalizedMonth(YearMonth month, BigDecimal accumulatedIndex) {}
}
