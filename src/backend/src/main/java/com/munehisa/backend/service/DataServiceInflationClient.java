package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.dto.dataservice.BrlInflationSeriesResponse;
import com.munehisa.backend.dto.dataservice.RawInflationDataPoint;
import com.munehisa.backend.dto.dataservice.UsdInflationSeriesResponse;
import com.munehisa.backend.exceptions.InflationDataServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.YearMonth;
import java.util.List;

@Component
public class DataServiceInflationClient {
    private final RestClient restClient;

    @Autowired
    public DataServiceInflationClient(
            @Value("${data-service.base-url}") String baseUrl,
            @Value("${data-service.api-key}") String apiKey
    ) {
        this(RestClient.builder(), baseUrl, apiKey);
    }

    // Package-private: lets tests bind a RestClient.Builder to a MockRestServiceServer
    // before it reaches here, without needing a Spring-managed RestClient.Builder bean
    // (this project's Spring Boot 4.1 setup doesn't pull in RestClient auto-configuration).
    DataServiceInflationClient(RestClient.Builder restClientBuilder, String baseUrl, String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public List<RawInflationDataPoint> fetchSeries(InflationCurrency currency) {
        try {
            return switch (currency) {
                case BRL -> fetchBrlSeries();
                case USD -> fetchUsdSeries();
            };
        } catch (RestClientException exception) {
            throw new InflationDataServiceException(currency, exception);
        }
    }

    private List<RawInflationDataPoint> fetchBrlSeries() {
        BrlInflationSeriesResponse response = restClient.get()
                .uri("/inflation/brl")
                .retrieve()
                .body(BrlInflationSeriesResponse.class);

        return response.monthlyData().stream()
                .map(point -> new RawInflationDataPoint(YearMonth.from(point.date()), point.rate()))
                .toList();
    }

    private List<RawInflationDataPoint> fetchUsdSeries() {
        UsdInflationSeriesResponse response = restClient.get()
                .uri("/inflation/usd")
                .retrieve()
                .body(UsdInflationSeriesResponse.class);

        return response.monthlyData().stream()
                .map(point -> new RawInflationDataPoint(YearMonth.from(point.date()), point.value()))
                .toList();
    }
}
