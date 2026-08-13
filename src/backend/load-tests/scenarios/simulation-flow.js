// The "create simulation, buy/sell, snapshot" flow. Unlike the other scenarios, this one
// does not touch the two shared read simulations from main.js#setup() — each iteration
// creates its own (alternating baseCurrency by __ITER, so both the BRL and USD warmed
// exchange-rate/asset caches get exercised), avoiding write contention with the read-only
// scenarios and with other simulation-flow iterations.
//
// Buys BUY_QUANTITY but sells only SELL_QUANTITY < BUY_QUANTITY, deliberately leaving a
// non-empty position: selling the *entire* position triggers SimulationService#sell's
// evictIfOrphaned call, which marks the ticker's AssetCacheService Postgres cache row orphaned
// the moment no position references it anymore. The row is only actually deleted by the daily
// cleanup job once it stays orphaned past the configured grace period (issue #142) - well
// outside a single load-test run - but a partial sell still exercises both POST .../buy and
// POST .../sell without ever orphaning the position, so the warmed cache entry stays
// unambiguously live for the rest of the run.
import { authedPost } from '../lib/http.js';
import { createSimulation, depositFixed } from '../lib/simulations.js';
import { TICKERS, DEPOSIT_AMOUNT } from '../config/identifiers.js';

const USD_TICKER = Object.keys(TICKERS).find((ticker) => TICKERS[ticker] === 'USD');
const BRL_TICKER = Object.keys(TICKERS).find((ticker) => TICKERS[ticker] === 'BRL');
const BUY_QUANTITY = 2;
const SELL_QUANTITY = 1;

export function simulationFlowScenario(data) {
    const tags = { scenario: 'simulation-flow' };
    const baseCurrency = __ITER % 2 === 0 ? 'USD' : 'BRL';
    const ticker = baseCurrency === 'USD' ? USD_TICKER : BRL_TICKER;
    const token = data.token;

    const simulation = createSimulation(`k6 simulation-flow VU${__VU} iter${__ITER}`, baseCurrency, token, tags);
    depositFixed(simulation.id, DEPOSIT_AMOUNT, false, token, tags);
    authedPost(`/simulations/${simulation.id}/buy`, { ticker, quantity: BUY_QUANTITY }, token, tags);
    authedPost(`/simulations/${simulation.id}/sell`, { ticker, quantity: SELL_QUANTITY }, token, tags);
    authedPost(`/simulations/${simulation.id}/snapshot`, {}, token, tags);
}
