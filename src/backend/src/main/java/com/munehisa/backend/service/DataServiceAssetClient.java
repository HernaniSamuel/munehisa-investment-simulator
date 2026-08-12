package com.munehisa.backend.service;

import com.munehisa.backend.dto.dataservice.AssetSeriesResponse;
import com.munehisa.backend.dto.dataservice.RawAssetMonthDataPoint;
import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.dto.dataservice.RawTickerSearchResult;
import com.munehisa.backend.dto.dataservice.TickerSearchResponse;
import com.munehisa.backend.exceptions.AssetDataServiceException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.TickerSearchUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.YearMonth;
import java.util.List;

@Component
public class DataServiceAssetClient {
    private final RestClient restClient;

    @Autowired
    public DataServiceAssetClient(
            @Value("${data-service.base-url}") String baseUrl,
            @Value("${data-service.api-key}") String apiKey
    ) {
        this(RestClient.builder(), baseUrl, apiKey);
    }

    // Package-private: lets tests bind a RestClient.Builder to a MockRestServiceServer
    // before it reaches here, without needing a Spring-managed RestClient.Builder bean
    // (this project's Spring Boot 4.1 setup doesn't pull in RestClient auto-configuration).
    DataServiceAssetClient(RestClient.Builder restClientBuilder, String baseUrl, String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    /**
     * Fetches a ticker's full monthly OHLCV (+ dividends/splits) history, plus its catalog
     * metadata (name, base currency, start date). A 404 (unknown ticker) is surfaced as its
     * own distinct exception - not conflated with any other upstream failure.
     */
    public RawAssetSeries fetchSeries(String ticker) {
        try {
            AssetSeriesResponse response = restClient.get()
                    .uri("/assets/{ticker}", ticker)
                    .retrieve()
                    .body(AssetSeriesResponse.class);

            return new RawAssetSeries(
                    response.ticker(),
                    response.name(),
                    response.baseCurrency(),
                    response.startDate(),
                    response.monthlyData().stream()
                            .map(point -> new RawAssetMonthDataPoint(
                                    YearMonth.from(point.date()), point.open(), point.high(), point.low(),
                                    point.close(), point.volume(), point.dividends(), point.splits()))
                            .toList(),
                    response.pricesSplitAdjusted());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new AssetNotFoundException(ticker, exception);
        } catch (RestClientException exception) {
            throw new AssetDataServiceException(ticker, exception);
        }
    }

    /**
     * Searches data-service for tickers matching a partial symbol or company name. Unlike
     * {@link #fetchSeries}, there is no not-found case - nothing matching is an empty list,
     * not a 404 - and no cache layer downstream of this call to distinguish "unavailable"
     * from "serve what we already have", so any transport failure is terminal here.
     */
    public List<RawTickerSearchResult> searchTickers(String query) {
        try {
            TickerSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/assets/search").queryParam("q", query).build())
                    .retrieve()
                    .body(TickerSearchResponse.class);

            return response.results().stream()
                    .map(match -> new RawTickerSearchResult(
                            match.ticker(), match.name(), match.exchange(), match.assetType()))
                    .toList();
        } catch (RestClientException exception) {
            throw new TickerSearchUnavailableException(exception);
        }
    }
}
