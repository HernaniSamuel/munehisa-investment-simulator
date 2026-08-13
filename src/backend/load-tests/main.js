// k6 entrypoint for the backend load test suite (issue #133). Run with:
//   docker compose --profile load-test run --rm k6
// or directly against a locally-published backend:
//   k6 run -e K6_BASE_URL=http://localhost:8000 src/backend/load-tests/main.js
// See README.md for full setup and how to read the summary.
import { authedGet, authedPost } from './lib/http.js';
import { createSimulation, depositFixed, withdrawFixed } from './lib/simulations.js';
import {
    TICKERS,
    CURRENCIES,
    SEARCH_QUERIES,
    LOAD_TEST_USER,
    DEPOSIT_AMOUNT,
    INFLATION_ROUNDTRIP_AMOUNT,
    VUS,
    DURATION,
} from './config/identifiers.js';

export { loginScenario } from './scenarios/login.js';
export { assetPriceHistoryScenario } from './scenarios/asset-price-history.js';
export { exchangeRateScenario } from './scenarios/exchange-rate.js';
export { inflationScenario } from './scenarios/inflation.js';
export { simulationFlowScenario } from './scenarios/simulation-flow.js';

// No `thresholds` are defined anywhere in this suite (by design — see README): the timed run
// always exits 0, and a human reads the summary for throughput/latency/error rate.
export const options = {
    scenarios: {
        login: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'loginScenario' },
        assetPriceHistory: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'assetPriceHistoryScenario',
        },
        exchangeRate: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'exchangeRateScenario',
        },
        inflation: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'inflationScenario',
        },
        simulationFlow: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'simulationFlowScenario',
        },
    },
};

// Runs once, before the timed scenarios above start. Warms the backend's Postgres cache for
// every fixed ticker/currency-pair/inflation-currency this suite uses, and exercises asset
// search (which has no cache to warm — see config/identifiers.js), so that once the timed
// run begins, none of the scenarios above trigger a data-service call.
export function setup() {
    const loginRes = authedPost('/auth/login', LOAD_TEST_USER, null, { scenario: 'setup' });
    const token = loginRes.json('token');
    const setupTags = { scenario: 'setup' };

    const usdSimulation = createSimulation('k6 shared USD simulation', 'USD', token, setupTags);
    const brlSimulation = createSimulation('k6 shared BRL simulation', 'BRL', token, setupTags);
    depositFixed(usdSimulation.id, DEPOSIT_AMOUNT, false, token, setupTags);
    depositFixed(brlSimulation.id, DEPOSIT_AMOUNT, false, token, setupTags);

    // Warm AssetCacheService for every fixed ticker, paired with its own-currency simulation
    // so this step never also depends on the exchange-rate warm-up below.
    Object.entries(TICKERS).forEach(([ticker, currency]) => {
        const simulationId = currency === 'USD' ? usdSimulation.id : brlSimulation.id;
        authedGet(`/simulations/${simulationId}/assets/${ticker}`, token, setupTags);
    });

    // Warm ExchangeRateCacheService for the BRL/USD pair with one cross-currency lookup.
    const [crossTicker, crossCurrency] = Object.entries(TICKERS)[0];
    const crossSimulationId = crossCurrency === 'USD' ? brlSimulation.id : usdSimulation.id;
    authedGet(`/simulations/${crossSimulationId}/assets/${crossTicker}`, token, setupTags);

    // Warm InflationCacheService for both supported currencies, then withdraw the applied
    // amount straight back out so the shared simulations start the timed run unaffected.
    CURRENCIES.forEach((currency) => {
        const simulationId = currency === 'USD' ? usdSimulation.id : brlSimulation.id;
        const deposit = depositFixed(simulationId, INFLATION_ROUNDTRIP_AMOUNT, true, token, setupTags);
        if (deposit && deposit.appliedAmount) {
            withdrawFixed(simulationId, deposit.appliedAmount, false, token, setupTags);
        }
    });

    // Asset search has no cache layer (SimulationService#searchTickers calls the
    // data-service client directly every time) — it is exercised here, once per fixed query,
    // and intentionally excluded from the timed scenarios above.
    SEARCH_QUERIES.forEach((query) => {
        authedGet(
            `/simulations/${usdSimulation.id}/assets/search?query=${encodeURIComponent(query)}`,
            token,
            { scenario: 'asset-search-setup' }
        );
    });

    console.log(
        `setup() complete, warm-up finished, timed scenarios starting now. simUsdId=${usdSimulation.id} simBrlId=${brlSimulation.id}`
    );
    return { token, simUsdId: usdSimulation.id, simBrlId: brlSimulation.id };
}

export function teardown(data) {
    console.log(
        `k6 load test finished. simUsdId=${data.simUsdId} simBrlId=${data.simBrlId}`
    );
}
