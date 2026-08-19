# 0008. Out-of-range month lookup asymmetry between assets and inflation/exchange rates

## Status

Accepted

## Context

A lookup for a target month can miss the available data on either side — the requested month
can be before the earliest cached data, or after the latest. `AssetCacheService.getAssetSeries()`
rejects a too-early month outright (`AssetPredatesStartDateException`) but silently substitutes
the latest cached month (flagged `truncated`) for a too-late one.
`ExchangeRateCacheService.getExchangeRate()` and `InflationCacheService.getInflationIndex()`
never reject either side — both always clamp to the nearest available month.

## Decision

We will reject an asset lookup for a month before the asset's own start date, since the asset
genuinely did not exist yet (there is no meaningful price to substitute) — but clamp
exchange-rate and inflation lookups to the nearest available month on either side, since the
underlying economic quantity (an exchange rate between two currencies, or a currency's
inflation) genuinely existed at the requested month even where cached data for it doesn't reach
that far, unlike an asset, which really did not exist before its listing.

## Consequences

For exchange rates and inflation, clamping to the nearest available month is a better
approximation than the alternatives — an error would force the user to manually hunt for the
month where data starts, and a fallback like a synthetic 1:1 exchange rate would be actively
misleading. For assets, rejecting an out-of-range purchase is the economically correct behavior
(nothing to substitute for a stock that didn't exist yet), and every listed asset's own price
series is otherwise always kept current, so there's no meaningful "asset exists but has no
recent data" case that also needs clamping.

The trade-off is a real accuracy loss on the inflation/exchange-rate side: a lookup that clamps
to, say, the earliest available inflation month for a much earlier target month reports that
month's index as if it applied, which is an approximation, not the true historical value.
