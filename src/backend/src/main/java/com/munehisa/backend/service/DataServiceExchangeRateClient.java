package com.munehisa.backend.service;

import com.munehisa.backend.dto.dataservice.ExchangeRateSeriesResponse;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.exceptions.ExchangeRateDataServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.YearMonth;
import java.util.List;

@Component
public class DataServiceExchangeRateClient {
    private final RestClient restClient;

    @Autowired
    public DataServiceExchangeRateClient(
            @Value("${data-service.base-url}") String baseUrl,
            @Value("${data-service.api-key}") String apiKey
    ) {
        this(RestClient.builder(), baseUrl, apiKey);
    }

    // Package-private: lets tests bind a RestClient.Builder to a MockRestServiceServer
    // before it reaches here, without needing a Spring-managed RestClient.Builder bean
    // (this project's Spring Boot 4.1 setup doesn't pull in RestClient auto-configuration).
    DataServiceExchangeRateClient(RestClient.Builder restClientBuilder, String baseUrl, String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    /**
     * Fetches the full monthly OHLC history for a currency pair. {@code baseCurrency} and
     * {@code quoteCurrency} must already be in canonical order - this client has no notion
     * of direction, that's decided one layer up.
     */
    public List<RawExchangeMonthDataPoint> fetchSeries(String baseCurrency, String quoteCurrency) {
        try {
            ExchangeRateSeriesResponse response = restClient.get()
                    .uri("/exchange/{from}/{to}", baseCurrency, quoteCurrency)
                    .retrieve()
                    .body(ExchangeRateSeriesResponse.class);

            return response.monthlyData().stream()
                    .map(point -> new RawExchangeMonthDataPoint(
                            YearMonth.from(point.date()), point.open(), point.high(), point.low(), point.close()))
                    .toList();
        } catch (RestClientException exception) {
            throw new ExchangeRateDataServiceException(baseCurrency, quoteCurrency, exception);
        }
    }
}
