// There is no standalone exchange-rate endpoint in this backend. Exchange-rate lookups only
// happen as a side effect of GET /simulations/{id}/assets/{ticker} when the ticker's native
// currency differs from the simulation's baseCurrency (SimulationService#convertCashBalance ->
// ExchangeRateCacheService). This scenario deliberately pairs each ticker with the OPPOSITE
// currency's shared simulation (PETR4.SA/BRL -> the USD simulation, AAPL/MSFT/USD -> the BRL
// simulation), forcing the real BRL/USD ExchangeRateCacheService lookup that main.js#setup()
// already warmed, isolating that lookup from the same-currency asset-price-history.js case.
import { authedGet } from '../lib/http.js';
import { TICKERS } from '../config/identifiers.js';

const TICKER_ENTRIES = Object.entries(TICKERS);

export function exchangeRateScenario(data) {
    const [ticker, currency] = TICKER_ENTRIES[__ITER % TICKER_ENTRIES.length];
    const simulationId = currency === 'USD' ? data.simBrlId : data.simUsdId;
    authedGet(`/simulations/${simulationId}/assets/${ticker}`, data.token, {
        scenario: 'exchange-rate',
    });
}
