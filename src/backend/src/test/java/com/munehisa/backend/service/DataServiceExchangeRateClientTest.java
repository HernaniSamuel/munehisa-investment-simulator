package com.munehisa.backend.service;

import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.exceptions.ExchangeRateDataServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class DataServiceExchangeRateClientTest {

    private static final String BASE_URL = "http://localhost:8001";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer mockServer;
    private DataServiceExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DataServiceExchangeRateClient(builder, BASE_URL, API_KEY);
    }

    @Test
    void fetchSeries_sendsApiKeyHeaderAndParsesOhlcAsBigDecimal() {
        mockServer.expect(requestTo(BASE_URL + "/exchange/BRL/USD"))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "symbol": "BRLUSD=X",
                          "from_currency": "BRL",
                          "to_currency": "USD",
                          "start_date": "2024-01-01",
                          "monthly_data": [
                            {"date": "2024-01-01", "open": "0.2000", "high": "0.2100", "low": "0.1950", "close": "0.2050"},
                            {"date": "2024-02-01", "open": "0.2050", "high": "0.2200", "low": "0.2000", "close": "0.2150"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawExchangeMonthDataPoint> series = client.fetchSeries("BRL", "USD");

        assertEquals(2, series.size());
        assertEquals(YearMonth.of(2024, 1), series.get(0).month());
        assertEquals(new BigDecimal("0.2000"), series.get(0).open());
        assertEquals(new BigDecimal("0.2100"), series.get(0).high());
        assertEquals(new BigDecimal("0.1950"), series.get(0).low());
        assertEquals(new BigDecimal("0.2050"), series.get(0).close());
        assertEquals(YearMonth.of(2024, 2), series.get(1).month());
        mockServer.verify();
    }

    @Test
    void fetchSeries_missingMonthIsSimplyAbsentFromResult() {
        mockServer.expect(requestTo(BASE_URL + "/exchange/BRL/USD"))
                .andRespond(withSuccess("""
                        {
                          "symbol": "BRLUSD=X",
                          "from_currency": "BRL",
                          "to_currency": "USD",
                          "start_date": "2024-01-01",
                          "monthly_data": [
                            {"date": "2024-01-01", "open": "0.20", "high": "0.21", "low": "0.19", "close": "0.20"},
                            {"date": "2024-03-01", "open": "0.22", "high": "0.23", "low": "0.21", "close": "0.22"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawExchangeMonthDataPoint> series = client.fetchSeries("BRL", "USD");

        assertEquals(2, series.size());
        assertEquals(YearMonth.of(2024, 1), series.get(0).month());
        assertEquals(YearMonth.of(2024, 3), series.get(1).month());
    }

    @Test
    void fetchSeries_upstream502_throwsExchangeRateDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/exchange/BRL/USD"))
                .andRespond(withBadGateway());

        assertThrows(ExchangeRateDataServiceException.class, () -> client.fetchSeries("BRL", "USD"));
    }

    @Test
    void fetchSeries_upstream401_throwsExchangeRateDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/exchange/BRL/USD"))
                .andRespond(withUnauthorizedRequest());

        assertThrows(ExchangeRateDataServiceException.class, () -> client.fetchSeries("BRL", "USD"));
    }
}
