// There is no standalone inflation endpoint in this backend. Inflation lookups only happen
// as a side effect of POST /simulations/{id}/deposits and /withdrawals when todaysMoney:true
// (InflationDeflationService -> InflationCacheService). Each iteration deposits a fixed
// amount as "today's money" (triggering the inflation lookup for whichever shared simulation
// it targets), then immediately withdraws the exact appliedAmount back out with
// todaysMoney:false so cash balance nets to zero and never drifts across a sustained run.
import { depositFixed, withdrawFixed } from '../lib/simulations.js';
import { INFLATION_ROUNDTRIP_AMOUNT } from '../config/identifiers.js';

export function inflationScenario(data) {
    const simulationId = __ITER % 2 === 0 ? data.simUsdId : data.simBrlId;
    const tags = { scenario: 'inflation' };
    const deposit = depositFixed(simulationId, INFLATION_ROUNDTRIP_AMOUNT, true, data.token, tags);
    if (deposit && deposit.appliedAmount) {
        withdrawFixed(simulationId, deposit.appliedAmount, false, data.token, tags);
    }
}
