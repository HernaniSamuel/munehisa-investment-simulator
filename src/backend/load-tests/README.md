# Backend load tests (k6)

k6 scripts that exercise the Java backend's own API — the surface the frontend calls — against
the Docker Compose stack. They cover the backend's main read and simulation flows: login,
asset search, asset price history, exchange-rate lookup, inflation lookup, and a simulation
create/buy/sell/snapshot flow.

The data-service is **not** a load-test target: it's a thin fetch/normalize layer in front of
real third-party APIs (Yahoo Finance, BCB, FRED), and hammering it under load would mean
hammering those upstream services. Every timed scenario in this suite reuses a small, fixed set
of tickers/currencies, warmed into the backend's Postgres cache by `main.js`'s `setup()` before
the timed run starts, so the timed portion only ever hits the backend's own cache.

No numeric pass/fail thresholds are defined anywhere in this suite — there's no baseline yet to
compare against. `k6 run` always exits `0` regardless of latency or error rate; read the summary
yourself (see [Reading the summary](#reading-the-summary) below).

## Prerequisites

- Docker Compose.
- The root `.env`, `src/backend/.env`, and `src/data-service/.env` filled in, per the root
  [README's "Running the full stack with Docker Compose"](../../../README.md) section. This
  suite doesn't add or need any new environment variables beyond what that section already
  documents.

## 1. Bring up the stack

```
docker compose up --build
```

(See the root README for details — not duplicated here.)

## 2. Seed the load-test user (one-time, or after a DB reset)

Login requires a *verified* user, and there's no API path to reach one — real registration
requires clicking an emailed verification link. [`seed/seed-load-test-user.sql`](seed/seed-load-test-user.sql)
inserts a single pre-verified user directly, using Postgres's `pgcrypto` extension to hash the
password (`crypt(..., gen_salt('bf'))` — compatible with the backend's `BCryptPasswordEncoder`).
It's idempotent, so re-running it (e.g. after `docker compose down -v`) is safe. Run it once
`backend` has started at least once (so Flyway has created the `users` table):

**bash**
```bash
set -a; source .env; set +a
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < src/backend/load-tests/seed/seed-load-test-user.sql
```

**PowerShell**
```powershell
Get-Content src\backend\load-tests\seed\seed-load-test-user.sql -Raw |
  docker compose exec -T postgres psql -U <POSTGRES_USER from your .env> -d <POSTGRES_DB from your .env>
```

The seeded credentials (`k6-loadtest@munehisa.invalid` / `Load-Test-P4ssw0rd!`) match
`LOAD_TEST_USER` in [`config/identifiers.js`](config/identifiers.js) by default. Override either
side with `LOAD_TEST_EMAIL`/`LOAD_TEST_PASSWORD` (see below) if you reseed with different values.

## 3. Run the load test

```
docker compose --profile load-test run --rm k6
```

`k6` is gated behind the `load-test` Compose profile and never starts on a plain
`docker compose up`/`up --build`. `run --rm` is one-shot: it runs `main.js` to completion,
prints the summary, and removes its container.

Override defaults with environment variables, e.g.:

```
docker compose --profile load-test run --rm -e LOAD_TEST_VUS=10 -e LOAD_TEST_DURATION=1m k6
```

| Variable | Default | Meaning |
|---|---|---|
| `K6_BASE_URL` | `http://backend:8000` (set by the compose service) | Backend base URL |
| `LOAD_TEST_VUS` | `5` | Virtual users per scenario |
| `LOAD_TEST_DURATION` | `30s` | Duration per scenario |
| `LOAD_TEST_EMAIL` / `LOAD_TEST_PASSWORD` | seeded defaults above | Credentials used by `setup()` and the `login` scenario |

`LOAD_TEST_VUS`/`LOAD_TEST_DURATION` are deliberately **not** named `K6_VUS`/`K6_DURATION`:
those are k6's own reserved environment variables and setting them overrides this suite's
explicit `scenarios` configuration entirely.

To run against a locally-published backend without Docker (e.g. `backend` started via your IDE),
run k6 directly instead: `k6 run -e K6_BASE_URL=http://localhost:8000 src/backend/load-tests/main.js`
(requires the [k6 CLI](https://k6.io/docs/get-started/installation/) installed locally).

## Known exception: asset search runs only during setup()

`GET /simulations/{id}/assets/search` has no Postgres cache layer at all —
`SimulationService#searchTickers` calls the data-service client directly on every call, by
existing backend design (out of scope for this issue to change). That makes it structurally
impossible to "warm" the way the other four lookups are warmed. It's exercised with the fixed
`SEARCH_QUERIES` set inside `setup()` — real, k6-measured requests that appear in the summary —
but deliberately **excluded** from the timed scenarios below, so it never contributes
data-service traffic once the timed run begins.

## Verifying "no new data-service traffic after warm-up"

In a second terminal:

```
docker compose logs -f data-service
```

Start the load test in the first terminal. You'll see one structured-JSON access-log line per
warm-up request (including the asset-search queries above) while `setup()` runs, then silence
for the rest of the run — none of the five timed scenarios (`login`, `assetPriceHistory`,
`exchangeRate`, `inflation`, `simulationFlow`) ever reach data-service once warm-up has
completed.

## Reading the summary

k6 prints a summary when the run finishes. Key metrics:

- `http_reqs` — total requests and requests/sec (throughput).
- `http_req_duration` `p(95)`/`p(99)` — latency percentiles.
- `http_req_failed` — error rate (non-2xx or network-level failures).
- `checks` — pass/fail counts for this suite's `check()` assertions (e.g. "login returned a
  token"). These are informational only — a failed check does not fail the k6 process or its
  exit code, since no `thresholds` are defined.

## Known limitations

- The bearer token `setup()` obtains is reused for the whole run and is not refreshed —
  a run longer than `JWT_EXPIRATION` (default 1h) will start seeing 401s partway through.
- `simulationFlow` creates a new simulation every iteration; repeated runs accumulate
  simulation rows in Postgres. `docker compose down -v` resets the database (re-seed the user
  afterwards).
