import threading
import time
from typing import Any

import requests

from data_service.exceptions import UpstreamFetchError
from data_service.schemas.asset import AssetSearchResponse, AssetSearchResult

_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search"
# No published SLA for this endpoint; chosen to fail in a reasonable time rather than
# hang, same reasoning as the FRED and BCB clients' explicit timeouts.
_TIMEOUT_SECONDS = 10.0
_MAX_RESULTS = 15
# Asked for more than we return so the filtering below (entries with no symbol, or not
# tradeable on Yahoo Finance) can't shrink a full page to fewer than _MAX_RESULTS.
_UPSTREAM_QUOTES_COUNT = 25
# Yahoo throttles/rejects requests carrying python-requests' default User-Agent; a
# browser-like one is required. This is why the call is raw `requests` rather than
# yfinance - yfinance exposes no search API to borrow its own session from.
_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
}

_CACHE_TTL_SECONDS = 300.0
_CACHE_MAX_ENTRIES = 256
# Only the results are cached, never the echoed query, so a cache hit still reflects the
# caller's own spelling of it. Guarded by a Lock because the route is a sync `def`, which
# FastAPI runs on a threadpool - concurrent callers really do hit this dict in parallel.
_cache: dict[str, tuple[float, list[AssetSearchResult]]] = {}
_cache_lock = threading.Lock()


def search_assets(query: str) -> AssetSearchResponse:
    """Ticker/name matches for a free-text query, from Yahoo Finance's public search
    endpoint. Capped at _MAX_RESULTS, with a short in-memory cache keyed by the
    trimmed/lowercased query so debounced typing doesn't fan out upstream.

    No "not found" case: a query nothing matches is an empty `results` list, not an error.

    Raises:
        UpstreamFetchError: the HTTP request failed (network, timeout, non-2xx including
            429) or the body wasn't parseable JSON.
    """
    cache_key = query.strip().lower()
    cached = _cache_get(cache_key)
    if cached is not None:
        return AssetSearchResponse(query=query, results=cached)

    results = _to_results(_fetch(query))
    _cache_put(cache_key, results)
    return AssetSearchResponse(query=query, results=results)


def _fetch(query: str) -> dict[str, Any]:
    params: dict[str, str | int] = {
        "q": query,
        "quotesCount": _UPSTREAM_QUOTES_COUNT,
        "newsCount": 0,
    }
    try:
        response = requests.get(
            _SEARCH_URL,
            params=params,
            headers=_HEADERS,
            timeout=_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        payload: dict[str, Any] = response.json()
        return payload
    except Exception as exc:
        raise UpstreamFetchError(f"Failed to search tickers for {query!r}: {exc}") from exc


def _to_results(payload: dict[str, Any]) -> list[AssetSearchResult]:
    # Yahoo's own field names (symbol/shortname/longname/quoteType) are translated to
    # generic ones here - this module is the only place in the system that knows them.
    results: list[AssetSearchResult] = []
    for quote in payload.get("quotes", []):
        ticker = quote.get("symbol")
        # `isYahooFinance: false` entries aren't tradeable/lookupable through the
        # exact-ticker endpoint, so offering them as picks would be a dead end.
        if not ticker or quote.get("isYahooFinance") is False:
            continue
        results.append(
            AssetSearchResult(
                ticker=ticker,
                name=quote.get("longname") or quote.get("shortname") or ticker,
                exchange=quote.get("exchange"),
                assetType=quote.get("quoteType"),
            )
        )
        if len(results) == _MAX_RESULTS:
            break
    return results


def _cache_get(key: str) -> list[AssetSearchResult] | None:
    now = time.monotonic()
    with _cache_lock:
        entry = _cache.get(key)
        if entry is None:
            return None
        stored_at, results = entry
        if now - stored_at >= _CACHE_TTL_SECONDS:
            del _cache[key]
            return None
        return results


def _cache_put(key: str, results: list[AssetSearchResult]) -> None:
    now = time.monotonic()
    with _cache_lock:
        _cache[key] = (now, results)
        # There's no eviction thread, so expired entries are reaped on write, and a hard
        # cap keeps a burst of distinct queries from growing this dict without bound.
        for expired in [k for k, (t, _) in _cache.items() if now - t >= _CACHE_TTL_SECONDS]:
            del _cache[expired]
        while len(_cache) > _CACHE_MAX_ENTRIES:
            del _cache[next(iter(_cache))]
