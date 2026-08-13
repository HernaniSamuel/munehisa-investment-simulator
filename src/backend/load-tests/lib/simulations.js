import { authedPost } from './http.js';
import { FIXED_MONTH } from '../config/identifiers.js';

export function createSimulation(name, baseCurrency, token, tags) {
    const res = authedPost(
        '/simulations',
        { name, baseCurrency, startMonth: FIXED_MONTH },
        token,
        tags
    );
    return res.json();
}

// todaysMoney:false skips InflationDeflationService entirely, so a plain funding deposit
// never touches InflationCacheService/data-service.
export function depositFixed(simulationId, amount, todaysMoney, token, tags) {
    const res = authedPost(
        `/simulations/${simulationId}/deposits`,
        { amount, todaysMoney },
        token,
        tags
    );
    return res.json();
}

export function withdrawFixed(simulationId, amount, todaysMoney, token, tags) {
    const res = authedPost(
        `/simulations/${simulationId}/withdrawals`,
        { amount, todaysMoney },
        token,
        tags
    );
    return res.json();
}
