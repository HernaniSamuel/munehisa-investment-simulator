package com.munehisa.backend.service;

import com.munehisa.backend.domain.asset.AssetCatalog;
import com.munehisa.backend.domain.asset.AssetMonthlyPrice;
import com.munehisa.backend.dto.AssetLookupResultDTO;
import com.munehisa.backend.dto.AssetMonthDataDTO;
import com.munehisa.backend.dto.dataservice.RawAssetMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.exceptions.AssetDataServiceException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.AssetPredatesStartDateException;
import com.munehisa.backend.exceptions.AssetUnavailableException;
import com.munehisa.backend.repository.AssetCatalogRepository;
import com.munehisa.backend.repository.AssetMonthlyPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetCacheService {
    private final AssetCatalogRepository assetCatalogRepository;
    private final AssetMonthlyPriceRepository assetMonthlyPriceRepository;
    private final DataServiceAssetClient dataServiceAssetClient;

    public AssetLookupResultDTO getAssetSeries(String tickerRaw, YearMonth targetMonth) {
        String ticker = tickerRaw.toUpperCase();

        ensureFreshData(ticker, targetMonth);

        AssetCatalog catalog = assetCatalogRepository.findByTicker(ticker)
                .orElseThrow(() -> new IllegalStateException(
                        "No asset catalog entry for " + ticker + " after a successful refresh"));

        YearMonth startMonth = YearMonth.from(catalog.getStartDate());
        if (targetMonth.isBefore(startMonth)) {
            throw new AssetPredatesStartDateException(ticker, targetMonth, catalog.getStartDate());
        }

        YearMonth latestCachedMonth = assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc(ticker)
                .orElseThrow(() -> new IllegalStateException(
                        "No cached monthly prices for " + ticker + " after a successful refresh"))
                .getReferenceMonth();

        boolean truncated = targetMonth.isAfter(latestCachedMonth);
        YearMonth effectiveMonth = truncated ? latestCachedMonth : targetMonth;

        List<AssetMonthDataDTO> series = assetMonthlyPriceRepository
                .findByTickerAndReferenceMonthLessThanEqualOrderByReferenceMonthAsc(ticker, effectiveMonth)
                .stream()
                .map(this::toDto)
                .toList();

        return new AssetLookupResultDTO(
                ticker, catalog.getName(), catalog.getBaseCurrency(), targetMonth, effectiveMonth, truncated, series);
    }

    private AssetMonthDataDTO toDto(AssetMonthlyPrice row) {
        return new AssetMonthDataDTO(
                row.getReferenceMonth(), row.getOpen(), row.getHigh(), row.getLow(), row.getClose(),
                row.getVolume(), row.getDividends(), row.getSplits());
    }

    private void ensureFreshData(String ticker, YearMonth targetMonth) {
        boolean hasAnyData = assetCatalogRepository.existsByTicker(ticker);

        if (!hasAnyData) {
            try {
                refetchAndUpsert(ticker);
            } catch (AssetDataServiceException exception) {
                throw new AssetUnavailableException(ticker, exception);
            }
            return;
        }

        YearMonth latestCachedMonth = assetMonthlyPriceRepository.findFirstByTickerOrderByReferenceMonthDesc(ticker)
                .orElseThrow()
                .getReferenceMonth();

        if (targetMonth.isAfter(latestCachedMonth)) {
            try {
                refetchAndUpsert(ticker);
            } catch (AssetNotFoundException | AssetDataServiceException exception) {
                log.warn("Asset refresh failed for {}, serving cached data", ticker, exception);
            }
        }
    }

    private void refetchAndUpsert(String ticker) {
        RawAssetSeries raw = dataServiceAssetClient.fetchSeries(ticker);
        upsertCatalog(ticker, raw);
        upsertMonthlySeries(ticker, raw.monthlyData());
    }

    private void upsertCatalog(String ticker, RawAssetSeries raw) {
        AssetCatalog catalog = assetCatalogRepository.findByTicker(ticker).orElseGet(AssetCatalog::new);
        catalog.setTicker(ticker);
        catalog.setName(raw.name());
        catalog.setBaseCurrency(raw.baseCurrency());
        catalog.setStartDate(raw.startDate());
        assetCatalogRepository.save(catalog);
    }

    private void upsertMonthlySeries(String ticker, List<RawAssetMonthDataPoint> raw) {
        Map<YearMonth, AssetMonthlyPrice> existing = assetMonthlyPriceRepository.findByTicker(ticker).stream()
                .collect(Collectors.toMap(AssetMonthlyPrice::getReferenceMonth, Function.identity()));

        List<AssetMonthlyPrice> toSave = raw.stream()
                .map(point -> {
                    AssetMonthlyPrice row = existing.getOrDefault(point.month(), new AssetMonthlyPrice());
                    row.setTicker(ticker);
                    row.setReferenceMonth(point.month());
                    row.setOpen(point.open());
                    row.setHigh(point.high());
                    row.setLow(point.low());
                    row.setClose(point.close());
                    row.setVolume(point.volume());
                    row.setDividends(point.dividends());
                    row.setSplits(point.splits());
                    return row;
                })
                .toList();

        assetMonthlyPriceRepository.saveAll(toSave);
    }
}
