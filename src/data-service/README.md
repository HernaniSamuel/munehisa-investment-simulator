# Munehisa — Data Service

Python/FastAPI microservice that fetches and normalizes third-party market data. This
module only *fetches and transforms* - no caching, no persistence, no business logic.
The Java backend ([`../backend`](../backend)) calls it synchronously over HTTP on a
cache-miss and owns everything downstream of the raw data.

## Stack

- **FastAPI** + **Pydantic v2** for the API and its schemas/validation
- **yfinance** / **pandas** for fetching and resampling market data
- **python-bcb** for fetching Brazil's official inflation index (IPCA) from BCB's SGS API
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
| `GET` | `/exchange/{from_currency}/{to_currency}` | `X-API-Key` | Monthly OHLC exchange-rate history for a currency pair, sourced from Yahoo Finance |
| `GET` | `/inflation/brl` | `X-API-Key` | Full monthly IPCA (Brazil's official inflation index) series, sourced from BCB's SGS series 433 |

- Unknown/invalid ticker or currency pair → `404` (not applicable to `/inflation/brl`:
  IPCA is a single fixed series code, not a user-supplied parameter that could be unknown)
- Missing/invalid API key → `401`
- Upstream/network failure or timeout → `502`
- `/exchange`: `from_currency == to_currency` returns a synthetic single-point series
  (`open = high = low = close = 1`, dated `1970-01-01` as a fixed sentinel) instead of
  querying yfinance at all
- `/inflation/brl`: each `rate` is the raw monthly rate as published (e.g. `0.5` meaning
  0.5% that month), not an accumulated/compounded figure between two dates - that
  compounding is business logic and stays in the Java backend

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

`services/yfinance_exchange_client.py` is ported the same way from
`external_apis/yfinance_exchange.py` (`YFinanceExchangeAPI`, same author/license). The
symbol construction and direct/inverse-with-rate-inversion fallback are mathematically
correct as-is and ported unchanged; the same fetch-error-vs-not-found distinction as
above is applied here too (`_fetch_symbol_data` there catches every exception broadly
and returns an empty DataFrame either way, conflating "pair doesn't exist" with "fetch
failed" - `AssetNotFoundError`/`UpstreamFetchError` are kept distinct here). No
decimal quantization is applied to exchange rates: real precision varies by pair (e.g.
`USDBRL=X` vs `USDJPY=X` carry different meaningful decimal counts) and doesn't follow
one universal convention the way stock OHLC does, so forcing a fixed decimal count
would be an uninformed business-precision decision this service shouldn't make.

`services/bcb_inflation_client.py` **replaces**, rather than ports, MineInvest's
`external_apis/inflation/brl_inflation.py`. MineInvest calls BCB's raw SGS HTTP API by
hand via `requests`; here, `python-bcb`'s `sgs.get(433, timeout=...)` wraps the same
data source with a much smaller surface, confirmed empirically to have the right shape
(a single float64 column literally named `'433'`, no NaNs, `DatetimeIndex` named
`Date`). Two things from the original were confirmed unnecessary and intentionally left
out:

- **No hardcoded start date.** `sgs.get(433)` with no `start`/`end` already returns the
  complete history starting exactly at `1980-01-01` - matching MineInvest's hardcoded
  floor exactly, so there's nothing to hardcode here.
- **No publication-schedule guessing.** MineInvest's `_get_adjusted_end_date` exists to
  work around MineInvest's own DB caching (guessing whether "no row for last month"
  means "not published yet" or "cache is stale"). This service never caches, so the
  live API's own response already reflects exactly what BCB has published - there's no
  stale-cache problem to guess around.

The accumulated-inflation-between-two-dates compounding in MineInvest's
`BCBInflationAPI.get_accumulated_inflation` is business logic and stays in Java; this
endpoint returns the raw monthly rate as published, not a compounded figure. An
explicit `timeout=` is required on every call: `sgs.get`'s own default is no timeout at
all, and a request against a bad series code was confirmed to take several minutes to
fail on its own rather than erroring out quickly.
