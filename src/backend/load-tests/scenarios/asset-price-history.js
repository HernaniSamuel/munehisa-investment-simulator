// Exercises GET /simulations/{id}/assets/{ticker} pairing each ticker with the shared
// simulation of the SAME base currency (AAPL/MSFT -> the USD simulation, PETR4.SA -> the BRL
// one). Matching currencies makes ExchangeRateCacheService short-circuit on fromCurrency ==
// toCurrency before any DB/data-service call, so this scenario isolates AssetCacheService
// cache-hit latency on its own — cross-currency conversion is exchange-rate.js's job.
import { authedGet } from '../lib/http.js';
import { TICKERS } from '../config/identifiers.js';

const TICKER_ENTRIES = Object.entries(TICKERS);

export function assetPriceHistoryScenario(data) {
    const [ticker, currency] = TICKER_ENTRIES[__ITER % TICKER_ENTRIES.length];
    const simulationId = currency === 'USD' ? data.simUsdId : data.simBrlId;
    authedGet(`/simulations/${simulationId}/assets/${ticker}`, data.token, {
        scenario: 'asset-price-history',
    });
}
