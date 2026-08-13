// Fixed, documented identifiers for the k6 load test suite. Nothing in this file is
// randomly generated: cache warm-up (see main.js#setup) and every timed scenario reuse
// exactly these tickers, currencies, queries, and credentials so a run is repeatable and
// the backend's Postgres cache never sees an identifier it wasn't warmed for.

// Tickers used throughout, mapped to their native currency. AAPL/MSFT are USD-denominated,
// PETR4.SA (B3/Brazil) is BRL-denominated. All three are exercised in the backend's own
// integration tests, so they're known-good against the data-service.
export const TICKERS = {
    AAPL: 'USD',
    MSFT: 'USD',
    'PETR4.SA': 'BRL',
};

// The only two currencies the domain model accepts anywhere (CreateSimulationRequestDTO's
// baseCurrency regex, InflationCurrency enum).
export const CURRENCIES = ['BRL', 'USD'];

// Partial-match queries against GET /simulations/{id}/assets/search. This endpoint has no
// Postgres cache layer (SimulationService#searchTickers calls the data-service client
// directly on every call), so it cannot be "warmed" the way the cache-backed lookups can.
// It is exercised only inside setup() (see main.js) with this fixed query set, not as a
// timed scenario, so it never contributes data-service traffic during the timed run.
export const SEARCH_QUERIES = ['AAPL', 'PETR4', 'MSFT'];

// Fixed startMonth for both shared read simulations (see main.js#setup). Postdates every
// ticker's real-world listing date and predates "now"; since neither shared simulation ever
// calls POST /simulations/{id}/advance, its current month never changes, so this stays the
// single, stable cache key for the whole run.
export const FIXED_MONTH = '2023-01';

export const BASE_URL = __ENV.K6_BASE_URL || 'http://backend:8000';

// Must stay byte-for-byte in sync with seed/seed-load-test-user.sql's INSERT — that script
// seeds exactly this email/password (bcrypt-hashed) as a pre-verified user, since there is
// no API path to reach a verified account (real registration requires clicking an emailed
// verification link).
export const LOAD_TEST_USER = {
    email: __ENV.LOAD_TEST_EMAIL || 'k6-loadtest@munehisa.invalid',
    password: __ENV.LOAD_TEST_PASSWORD || 'Load-Test-P4ssw0rd!',
};

// Deposited once into each shared simulation during setup() (todaysMoney:false, no inflation
// call) so scenarios/simulation-flow.js's buy/sell always has funds available.
export const DEPOSIT_AMOUNT = 1000000;

// Round-tripped by scenarios/inflation.js on every iteration; kept small since it's applied
// and immediately withdrawn back out.
export const INFLATION_ROUNDTRIP_AMOUNT = 1000;

// Not named K6_VUS/K6_DURATION: those are k6's own reserved env vars that set a top-level
// options.vus/options.duration shorthand, which silently overrides an explicit `scenarios`
// block entirely (confirmed by running this suite — k6 refused to start with "'default' not
// found in exports" once K6_VUS/K6_DURATION were set, since it stopped seeing the scenarios
// below). LOAD_TEST_VUS/LOAD_TEST_DURATION avoid that collision.
export const VUS = Number(__ENV.LOAD_TEST_VUS || 5);
export const DURATION = __ENV.LOAD_TEST_DURATION || '30s';
