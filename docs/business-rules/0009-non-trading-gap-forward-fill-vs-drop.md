# 0009. Non-trading-gap handling: forward-fill OHLC, but not volume/dividends/splits or a missing CPI point

## Status

Accepted

## Context

A month with no trading activity for an asset or currency pair, and a month FRED never
published a CPI value for (e.g. the October 2025 series gap tied to the US government
shutdown), both leave a gap in an otherwise-monthly series. `yfinance_client.py`'s
`fetch_asset()` forward-fills Open/High/Low/Close for a gap month from the last real price, but
leaves Volume, Dividends, and Splits at their "nothing happened" value rather than carrying the
previous month's values forward; `yfinance_exchange_client.py`'s `fetch_exchange_rate()`
forward-fills its OHLC the same way (it has no volume/dividend/split columns to begin with).
`fred_inflation_client.py`'s `fetch_usd_inflation()` drops a genuinely missing CPI row entirely
rather than forward-filling it.

## Decision

We will forward-fill OHLC price data for a gap month, since a price is a persisting quantity —
if nothing traded, the price didn't change, so repeating the last close is a reasonable
stand-in, and leaving price blank would distort the simulation more than a stale-but-real price
would. We will not forward-fill Volume, Dividends, or Splits, since each of those represents a
discrete event that either happened or didn't in that month — a month with no trading really
did have zero volume, no dividend, and no split, and carrying forward the previous month's
value would fabricate an event that never occurred. For the same reason, a genuinely missing
CPI point is dropped rather than forward-filled — it represents a value FRED hasn't published
(or never will), not "no inflation that month," so inventing one would fabricate a data point
the source never provided.

## Consequences

Price continuity is preserved for gap months without ever inventing volume, dividend, or split
events that didn't happen — each field is treated according to whether it represents a
persisting value or a discrete occurrence. The missing-CPI case is never persisted into the
shared inflation cache either; a lookup for that exact month instead resolves through the same
clamp-to-nearest-available behavior as [0008](0008-out-of-range-month-lookup-asymmetry.md),
rather than through a separate forward-fill step.

The trade-off: forward-filled OHLC for an illiquid asset can understate how stale a "current"
price really is if a gap runs for several months, since the series doesn't visibly distinguish
a forward-filled month from a month with real trading — only `AssetLookupResultDTO.truncated`
flags the case where the gap extends all the way to the requested month, not a gap in the
middle of the series.
