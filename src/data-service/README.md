# Munehisa — Data Service

Python/FastAPI microservice that fetches and normalizes third-party market data. This
module only *fetches and transforms* - no caching, no persistence, no business logic.
The Java backend ([`../backend`](../backend)) calls it synchronously over HTTP on a
cache-miss and owns everything downstream of the raw data.

## Stack

- **FastAPI** + **Pydantic v2** for the API and its schemas/validation
- **yfinance** / **pandas** for fetching and resampling market data
- **pytest** for tests, **ruff** for linting, **mypy** for type-checking

## Running locally

**Prerequisites:** Python 3.14.

1. Activate the virtualenv (already created by PyCharm at `.venv/`) and install the
   package with its dev dependencies:
   ```
   .venv\Scripts\activate
   pip install -e ".[dev]"
   ```
2. Copy the environment template and fill in your own API key:
   ```
   cp .env.example .env
   ```
   `DATA_SERVICE_API_KEY` has no default and is required - every request must send it
   back as the `X-API-Key` header. `DATA_SERVICE_PORT` is optional (defaults to `8001`).
3. Run the service - two ways, depending on whether you want autoreload:
   ```
   # Dev, with autoreload (port is fixed at 8001 here - the uvicorn CLI doesn't read
   # DATA_SERVICE_PORT; pass --port explicitly if you need a different one):
   uvicorn data_service.main:app --reload --port 8001

   # No autoreload, honors DATA_SERVICE_PORT from .env:
   python -m data_service.main
   ```
   Both bind to `127.0.0.1` (loopback-only) - there's no network-level isolation yet
   (deferred to a future hosting decision), so the API key is the only thing standing
   between this service and any request that reaches it.

### API docs (Swagger UI)

Always enabled (this service isn't deployed publicly yet - see the root README's
"Running locally" section for the analogous Java backend flow):

```
http://localhost:8001/docs
```

### Verifying changes

```
ruff check .   # lint (also run in CI)
mypy .         # typecheck (also run in CI)
pytest         # fast tests, no network calls (also run in CI)
pytest -m live # slow: hits the real Yahoo Finance API, run manually only
```

## Architecture

```
data_service/
  routes/     FastAPI routers (HTTP concerns only, delegate to services)
  services/   Fetch + transform logic (e.g. yfinance_client.py)
  schemas/    Pydantic request/response models, including the shared error shape
  security.py    X-API-Key header dependency
  config.py       Environment-driven settings
  main.py         App wiring: router registration, exception -> HTTP status mapping
```

Every error response - unauthorized, not-found, upstream failure - comes back as
`{"status": "...", "message": "..."}`, mirroring the Java backend's `RestErrorMessage`
shape so both services are consistent for any client.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/assets/{ticker}` | `X-API-Key` | Monthly OHLCV history (+ dividends/splits) for a ticker, sourced from Yahoo Finance |

- Unknown/invalid ticker → `404`
- Missing/invalid API key → `401`
- yfinance/network failure → `502`

## Porting notes (MineInvest)

The transform logic in `services/yfinance_client.py` is ported from
[MineInvest](https://github.com/HernaniSamuel/MineInvest)'s
`external_apis/yfinance_client.py` (same author, Apache-2.0), with three deliberate
deviations found while porting and verified against the live API:

- **Splits aggregation was actually broken.** MineInvest resamples the daily "Stock
  Splits" column with `'prod'` (product), assuming non-split days are filled with
  `1.0` (the multiplicative identity). Live yfinance data (checked against real AAPL
  history, including its known 2014 7-for-1 and 2020 4-for-1 splits) fills non-split
  days with `0.0` instead - so the product of any month's daily values is `0.0`
  *every single month*, silently hiding every real split and reporting a bogus
  `"0.0"` split on every month instead. Fixed here by aggregating with `'sum'` and
  checking `> 0`, the same convention already used for dividends (see
  `test_fetch_asset_no_split_across_many_days_stays_none` for the regression test).
- **Fetch errors vs. "not found" are now distinguished.** MineInvest catches *all*
  fetch errors - including genuine network/upstream failures - as "ticker not found".
  Here, upstream fetch failures surface as `502`; only a genuinely empty history
  (`hist.empty`) is `404`.
- **Volume is no longer forward-filled into gap months.** A month with zero trading
  days had zero volume; carrying the previous month's volume forward would
  misrepresent what actually happened. OHLC price columns are still forward-filled
  (there's no meaningful price data for a gap month otherwise).
