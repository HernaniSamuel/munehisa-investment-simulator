# 0006. Postgres-backed, lazily-refreshed cache for third-party market data

## Status

Accepted

## Context

Asset prices, exchange rates, and inflation data all come from the third-party sources behind
the data-service (see [0004](0004-python-data-service.md)), including the Yahoo Finance source
that sits in a legal gray zone. Fetching live on every request risked abusing that source —
both in terms of being rate-limited or blocked, and in terms of retaining/redistributing more
data than is actually in use. Live fetching would also make the app fully dependent on the
upstream APIs staying up, and on their (much higher) latency compared to a local read.

Separately, every simulation operates over the same kind of time period, so data fetched for one
simulation (e.g. an asset's price for a given month) is reusable by any other simulation that
needs the same asset/period — advancing a simulation by a month should be able to reuse an
already-cached price instead of triggering a fresh fetch.

## Decision

We will cache asset price, exchange-rate, and inflation data in Postgres, shared across all
simulations, refreshed lazily — fetched from the data-service only on demand, when a request
needs data that's missing or stale, rather than via a scheduled bulk-refresh job. Cache entries
no longer referenced by any active position are evicted, not to save storage (the data volume is
too light to need that), but to avoid keeping or redistributing third-party data beyond what's
actively in use.

## Consequences

Reads that hit the cache are fast and reliable compared to a live fetch against `yfinance`, the
app keeps working even if an upstream API is temporarily down, and simulations sharing an
overlapping period reuse the same cached data instead of each re-fetching it independently. This
also keeps request volume against the (rate-sensitive, gray-zone) data source down to only what's
genuinely needed.

The trade-off: this pushes real infrastructure and complexity onto the application itself — a
shared cache, on-demand refresh logic, and an eviction mechanism — instead of the much simpler
approach of firing a fresh request every time a simulation needs a price. That complexity was
accepted specifically for the API-abuse, latency, and reliability reasons above, not because the
data volume itself demanded it.
