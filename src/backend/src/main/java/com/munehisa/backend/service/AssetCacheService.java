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
import com.munehisa.backend.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetCacheService {
    private final AssetCatalogRepository assetCatalogRepository;
    private final AssetMonthlyPriceRepository assetMonthlyPriceRepository;
    private final DataServiceAssetClient dataServiceAssetClient;
    private final PositionRepository positionRepository;

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

    // Assumes assetId already exists in asset_catalog (the only route into `position` is via
    // its FK to asset_catalog.id); a nonexistent assetId is out of scope for #70 and will
    // surface as EmptyResultDataAccessException from deleteById if ever violated.
    public void evictIfOrphaned(UUID assetId) {
        if (positionRepository.existsByAssetId(assetId)) {
            return;
        }
        assetCatalogRepository.deleteById(assetId);
        log.info("Evicted orphaned asset cache entry {}", assetId);
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
        assetCatalogRepository.upsert(UUID.randomUUID(), ticker, raw.name(), raw.baseCurrency(), raw.startDate());
    }

    private void upsertMonthlySeries(String ticker, List<RawAssetMonthDataPoint> raw) {
        for (RawAssetMonthDataPoint point : raw) {
            assetMonthlyPriceRepository.upsert(
                    UUID.randomUUID(), ticker, point.month().atDay(1),
                    point.open(), point.high(), point.low(), point.close(),
                    point.volume(), point.dividends(), point.splits());
        }
    }
}
