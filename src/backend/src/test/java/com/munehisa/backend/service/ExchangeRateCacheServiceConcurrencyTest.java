package com.munehisa.backend.service;

import com.munehisa.backend.domain.exchangerate.ExchangeRate;
import com.munehisa.backend.dto.dataservice.RawExchangeMonthDataPoint;
import com.munehisa.backend.repository.ExchangeRateRepository;
import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for #83: two concurrent cold-start requests for the same never-cached
 * currency pair used to both pass ensureFreshData's existsByBaseCurrencyAndQuoteCurrency check,
 * both attempt to insert the same exchange_rate rows, and collide on the unique constraint with
 * an unhandled DataIntegrityViolationException (surfaced as a 500 to the caller). Runs against
 * a real Postgres instance - the atomic upsert fix's correctness depends on actual
 * ON CONFLICT DO UPDATE behavior, which mocked repositories can't exercise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ExchangeRateCacheService.class)
@ActiveProfiles("test")
@Tag("integration")
// @DataJpaTest wraps each test method (and its @BeforeEach/@AfterEach) in one transaction
// that rolls back by default. The worker threads below commit for real on their own
// connections regardless, so relying on that rollback for @AfterEach cleanup would silently
// no-op it and leak committed rows into the shared container for later tests. Disabling the
// wrapper transaction makes every call - main thread and worker threads alike - commit
// normally, so cleanup actually persists.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExchangeRateCacheServiceConcurrencyTest extends SharedPostgresContainer {

    private static final String BASE = "XCT";
    private static final String QUOTE = "XCU";

    @Autowired
    private ExchangeRateCacheService exchangeRateCacheService;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;
    @MockitoBean
    private DataServiceExchangeRateClient dataServiceExchangeRateClient;

    @AfterEach
    void cleanUp() {
        exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(BASE, QUOTE)
                .forEach(row -> exchangeRateRepository.deleteById(row.getId()));
    }

    @Test
    void concurrentColdStartRefresh_forSameNeverCachedPair_upsertsWithoutConstraintViolation() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(dataServiceExchangeRateClient.fetchSeries(BASE, QUOTE)).thenAnswer(invocation -> {
            // Rendezvous both threads here so they reach the persistence step at the same
            // time, reproducing the race instead of relying on incidental timing.
            barrier.await(5, TimeUnit.SECONDS);
            return List.of(
                    new RawExchangeMonthDataPoint(YearMonth.of(2024, 1),
                            new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("1.00")),
                    new RawExchangeMonthDataPoint(YearMonth.of(2024, 2),
                            new BigDecimal("1.10"), new BigDecimal("1.10"), new BigDecimal("1.10"), new BigDecimal("1.10")));
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> {
                exchangeRateCacheService.getExchangeRate(BASE, QUOTE, YearMonth.of(2024, 2));
                return null;
            };
            List<Future<Void>> futures = pool.invokeAll(List.of(task, task));
            for (Future<Void> future : futures) {
                assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdown();
        }

        List<ExchangeRate> rows = exchangeRateRepository.findByBaseCurrencyAndQuoteCurrency(BASE, QUOTE);
        assertEquals(2, rows.size());
        assertEquals(rows.size(), rows.stream().map(ExchangeRate::getReferenceMonth).distinct().count());
    }
}
