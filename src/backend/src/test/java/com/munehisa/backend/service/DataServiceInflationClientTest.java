package com.munehisa.backend.service;

import com.munehisa.backend.domain.inflation.InflationCurrency;
import com.munehisa.backend.dto.dataservice.RawInflationDataPoint;
import com.munehisa.backend.exceptions.InflationDataServiceException;
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

class DataServiceInflationClientTest {

    private static final String BASE_URL = "http://localhost:8001";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer mockServer;
    private DataServiceInflationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DataServiceInflationClient(builder, BASE_URL, API_KEY);
    }

    @Test
    void fetchSeries_brl_sendsApiKeyHeaderAndParsesRateAsBigDecimal() {
        mockServer.expect(requestTo(BASE_URL + "/inflation/brl"))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "start_date": "1980-01-01",
                          "monthly_data": [
                            {"date": "1980-01-01", "rate": "6.62"},
                            {"date": "1980-02-01", "rate": "4.62"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawInflationDataPoint> series = client.fetchSeries(InflationCurrency.BRL);

        assertEquals(2, series.size());
        assertEquals(YearMonth.of(1980, 1), series.get(0).month());
        assertEquals(new BigDecimal("6.62"), series.get(0).rawValue());
        assertEquals(YearMonth.of(1980, 2), series.get(1).month());
        assertEquals(new BigDecimal("4.62"), series.get(1).rawValue());
        mockServer.verify();
    }

    @Test
    void fetchSeries_usd_sendsApiKeyHeaderAndParsesValueAsBigDecimal() {
        mockServer.expect(requestTo(BASE_URL + "/inflation/usd"))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "start_date": "1947-01-01",
                          "monthly_data": [
                            {"date": "1947-01-01", "value": "21.48"},
                            {"date": "1947-02-01", "value": "21.62"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawInflationDataPoint> series = client.fetchSeries(InflationCurrency.USD);

        assertEquals(2, series.size());
        assertEquals(new BigDecimal("21.48"), series.get(0).rawValue());
        assertEquals(new BigDecimal("21.62"), series.get(1).rawValue());
        mockServer.verify();
    }

    @Test
    void fetchSeries_usd_missingMonthIsSimplyAbsentFromResult() {
        // Mirrors the real Oct-2025 CPI-U shutdown gap documented in the data-service README:
        // a month absent from monthly_data must stay absent, never interpolated.
        mockServer.expect(requestTo(BASE_URL + "/inflation/usd"))
                .andRespond(withSuccess("""
                        {
                          "start_date": "2025-09-01",
                          "monthly_data": [
                            {"date": "2025-09-01", "value": "324.0"},
                            {"date": "2025-11-01", "value": "325.0"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawInflationDataPoint> series = client.fetchSeries(InflationCurrency.USD);

        assertEquals(2, series.size());
        assertEquals(YearMonth.of(2025, 9), series.get(0).month());
        assertEquals(YearMonth.of(2025, 11), series.get(1).month());
    }

    @Test
    void fetchSeries_upstream502_throwsInflationDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/inflation/brl"))
                .andRespond(withBadGateway());

        assertThrows(InflationDataServiceException.class, () -> client.fetchSeries(InflationCurrency.BRL));
    }

    @Test
    void fetchSeries_upstream401_throwsInflationDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/inflation/usd"))
                .andRespond(withUnauthorizedRequest());

        assertThrows(InflationDataServiceException.class, () -> client.fetchSeries(InflationCurrency.USD));
    }
}
