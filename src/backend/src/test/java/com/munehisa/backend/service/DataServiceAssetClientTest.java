package com.munehisa.backend.service;

import com.munehisa.backend.dto.dataservice.RawAssetSeries;
import com.munehisa.backend.dto.dataservice.RawTickerSearchResult;
import com.munehisa.backend.exceptions.AssetDataServiceException;
import com.munehisa.backend.exceptions.AssetNotFoundException;
import com.munehisa.backend.exceptions.TickerSearchUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class DataServiceAssetClientTest {

    private static final String BASE_URL = "http://localhost:8001";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer mockServer;
    private DataServiceAssetClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new DataServiceAssetClient(builder, BASE_URL, API_KEY);
    }

    @Test
    void fetchSeries_sendsApiKeyHeaderAndParsesFullResponse() {
        mockServer.expect(requestTo(BASE_URL + "/assets/AAPL"))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "ticker": "AAPL",
                          "name": "Apple Inc.",
                          "base_currency": "USD",
                          "start_date": "1980-12-01",
                          "monthly_data": [
                            {"date": "2024-01-01", "open": "180.00", "high": "190.00", "low": "175.00", "close": "185.00", "volume": 1000000, "dividends": null, "splits": null},
                            {"date": "2024-02-01", "open": "185.00", "high": "195.00", "low": "180.00", "close": "192.00", "volume": 1200000, "dividends": "0.24", "splits": null}
                          ],
                          "prices_split_adjusted": true
                        }
                        """, MediaType.APPLICATION_JSON));

        RawAssetSeries series = client.fetchSeries("AAPL");

        assertEquals("AAPL", series.ticker());
        assertEquals("Apple Inc.", series.name());
        assertEquals("USD", series.baseCurrency());
        assertEquals(LocalDate.of(1980, 12, 1), series.startDate());
        assertTrue(series.pricesSplitAdjusted());
        assertEquals(2, series.monthlyData().size());
        assertEquals(YearMonth.of(2024, 1), series.monthlyData().get(0).month());
        assertEquals(new BigDecimal("180.00"), series.monthlyData().get(0).open());
        assertEquals(1_000_000L, series.monthlyData().get(0).volume());
        assertNull(series.monthlyData().get(0).dividends());
        assertEquals(new BigDecimal("0.24"), series.monthlyData().get(1).dividends());
        mockServer.verify();
    }

    @Test
    void fetchSeries_unknownTicker404_throwsAssetNotFoundException() {
        mockServer.expect(requestTo(BASE_URL + "/assets/NOTATICKER"))
                .andRespond(withResourceNotFound());

        assertThrows(AssetNotFoundException.class, () -> client.fetchSeries("NOTATICKER"));
    }

    @Test
    void fetchSeries_upstream502_throwsAssetDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/assets/AAPL"))
                .andRespond(withBadGateway());

        assertThrows(AssetDataServiceException.class, () -> client.fetchSeries("AAPL"));
    }

    @Test
    void fetchSeries_upstream401_throwsAssetDataServiceException() {
        mockServer.expect(requestTo(BASE_URL + "/assets/AAPL"))
                .andRespond(withUnauthorizedRequest());

        assertThrows(AssetDataServiceException.class, () -> client.fetchSeries("AAPL"));
    }

    @Test
    void searchTickers_sendsApiKeyHeaderAndQueryParamAndParsesResults() {
        mockServer.expect(requestTo(BASE_URL + "/assets/search?q=petr"))
                .andExpect(header("X-API-Key", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "query": "petr",
                          "results": [
                            {"ticker": "PETR4.SA", "name": "Petrobras", "exchange": "SAO", "assetType": "EQUITY"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawTickerSearchResult> results = client.searchTickers("petr");

        assertEquals(1, results.size());
        RawTickerSearchResult result = results.get(0);
        assertEquals("PETR4.SA", result.ticker());
        assertEquals("Petrobras", result.name());
        assertEquals("SAO", result.exchange());
        assertEquals("EQUITY", result.assetType());
        mockServer.verify();
    }

    @Test
    void searchTickers_noMatches_returnsEmptyList() {
        mockServer.expect(requestTo(BASE_URL + "/assets/search?q=zzz"))
                .andRespond(withSuccess("""
                        {"query": "zzz", "results": []}
                        """, MediaType.APPLICATION_JSON));

        List<RawTickerSearchResult> results = client.searchTickers("zzz");

        assertTrue(results.isEmpty());
    }

    @Test
    void searchTickers_encodesQueryWithSpaces() {
        mockServer.expect(requestTo(BASE_URL + "/assets/search?q=petro%20bras"))
                .andRespond(withSuccess("""
                        {"query": "petro bras", "results": []}
                        """, MediaType.APPLICATION_JSON));

        client.searchTickers("petro bras");

        mockServer.verify();
    }

    @Test
    void searchTickers_upstream502_throwsTickerSearchUnavailableException() {
        mockServer.expect(requestTo(BASE_URL + "/assets/search?q=petr"))
                .andRespond(withBadGateway());

        assertThrows(TickerSearchUnavailableException.class, () -> client.searchTickers("petr"));
    }

    @Test
    void searchTickers_upstream401_throwsTickerSearchUnavailableException() {
        mockServer.expect(requestTo(BASE_URL + "/assets/search?q=petr"))
                .andRespond(withUnauthorizedRequest());

        assertThrows(TickerSearchUnavailableException.class, () -> client.searchTickers("petr"));
    }
}
