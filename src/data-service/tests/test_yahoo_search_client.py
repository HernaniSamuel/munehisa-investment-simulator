import json
import time

import pytest
import requests

from data_service.exceptions import UpstreamFetchError
from data_service.services import yahoo_search_client


@pytest.fixture(autouse=True)
def _clear_cache():
    yahoo_search_client._cache.clear()
    yield
    yahoo_search_client._cache.clear()


class _FakeResponse:
    def __init__(self, payload=None, status_code: int = 200, text: str = ""):
        self._payload = payload
        self.status_code = status_code
        self.text = text if payload is None else json.dumps(payload)

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise requests.exceptions.HTTPError(f"{self.status_code} error")

    def json(self):
        if self._payload is None:
            raise ValueError("no JSON payload configured")
        return self._payload


def _quotes_payload(quotes):
    return {"quotes": quotes}


def test_maps_yahoo_quotes_to_generic_fields(monkeypatch):
    payload = _quotes_payload(
        [
            {
                "symbol": "PETR4.SA",
                "longname": "Petróleo Brasileiro S.A. - Petrobras",
                "shortname": "PETROBRAS PN",
                "exchange": "SAO",
                "quoteType": "EQUITY",
            }
        ]
    )
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(payload))

    response = yahoo_search_client.search_assets("petr")

    assert response.query == "petr"
    assert len(response.results) == 1
    result = response.results[0]
    assert result.ticker == "PETR4.SA"
    assert result.name == "Petróleo Brasileiro S.A. - Petrobras"
    assert result.exchange == "SAO"
    assert result.asset_type == "EQUITY"


def test_prefers_longname_then_shortname_then_symbol(monkeypatch):
    payload = _quotes_payload(
        [
            {"symbol": "AAA", "shortname": "AAA Short", "quoteType": "EQUITY"},
            {"symbol": "BBB", "quoteType": "EQUITY"},
        ]
    )
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(payload))

    response = yahoo_search_client.search_assets("a")

    names = {result.ticker: result.name for result in response.results}
    assert names["AAA"] == "AAA Short"
    assert names["BBB"] == "BBB"


def test_caps_results_at_15(monkeypatch):
    quotes = [
        {"symbol": f"TICK{i}", "shortname": f"Ticker {i}", "quoteType": "EQUITY"}
        for i in range(25)
    ]
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(_quotes_payload(quotes)))

    response = yahoo_search_client.search_assets("tick")

    assert len(response.results) == 15


def test_skips_entries_without_a_symbol(monkeypatch):
    payload = _quotes_payload(
        [
            {"shortname": "No symbol here", "quoteType": "EQUITY"},
            {"symbol": "REAL", "shortname": "Real ticker", "quoteType": "EQUITY"},
        ]
    )
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(payload))

    response = yahoo_search_client.search_assets("real")

    assert [r.ticker for r in response.results] == ["REAL"]


def test_skips_non_yahoo_finance_entries(monkeypatch):
    payload = _quotes_payload(
        [
            {"symbol": "FAKE", "shortname": "Not tradeable", "isYahooFinance": False},
            {"symbol": "REAL", "shortname": "Real ticker", "isYahooFinance": True},
        ]
    )
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(payload))

    response = yahoo_search_client.search_assets("x")

    assert [r.ticker for r in response.results] == ["REAL"]


def test_no_matches_returns_empty_results(monkeypatch):
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(_quotes_payload([])))

    response = yahoo_search_client.search_assets("zzzznomatch")

    assert response.results == []


def test_identical_query_within_ttl_serves_cache_without_second_upstream_call(monkeypatch):
    call_count = {"count": 0}

    def _fake_get(*args, **kwargs):
        call_count["count"] += 1
        return _FakeResponse(_quotes_payload([{"symbol": "AAA", "shortname": "AAA Inc"}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    yahoo_search_client.search_assets("aaa")
    yahoo_search_client.search_assets("aaa")

    assert call_count["count"] == 1


def test_cache_key_ignores_case_and_surrounding_whitespace(monkeypatch):
    call_count = {"count": 0}

    def _fake_get(*args, **kwargs):
        call_count["count"] += 1
        return _FakeResponse(_quotes_payload([{"symbol": "AAA", "shortname": "AAA Inc"}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    yahoo_search_client.search_assets("PETR")
    yahoo_search_client.search_assets(" petr ")

    assert call_count["count"] == 1


def test_cached_response_echoes_each_callers_own_query_spelling(monkeypatch):
    monkeypatch.setattr(
        requests,
        "get",
        lambda *a, **k: _FakeResponse(_quotes_payload([{"symbol": "AAA", "shortname": "AAA Inc"}])),
    )

    yahoo_search_client.search_assets("PETR")
    second = yahoo_search_client.search_assets(" petr ")

    assert second.query == " petr "


def test_refetches_after_ttl_expires(monkeypatch):
    call_count = {"count": 0}

    def _fake_get(*args, **kwargs):
        call_count["count"] += 1
        return _FakeResponse(_quotes_payload([{"symbol": "AAA", "shortname": "AAA Inc"}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    fake_now = {"t": 1000.0}
    monkeypatch.setattr(time, "monotonic", lambda: fake_now["t"])

    yahoo_search_client.search_assets("aaa")
    fake_now["t"] += yahoo_search_client._CACHE_TTL_SECONDS + 1
    yahoo_search_client.search_assets("aaa")

    assert call_count["count"] == 2


def test_distinct_queries_are_cached_separately(monkeypatch):
    call_count = {"count": 0}

    def _fake_get(url, params=None, **kwargs):
        call_count["count"] += 1
        symbol = "AAA" if params["q"] == "aaa" else "BBB"
        return _FakeResponse(_quotes_payload([{"symbol": symbol, "shortname": symbol}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    yahoo_search_client.search_assets("aaa")
    yahoo_search_client.search_assets("bbb")

    assert call_count["count"] == 2


def test_upstream_failure_is_not_cached(monkeypatch):
    call_count = {"count": 0}

    def _fake_get(*args, **kwargs):
        call_count["count"] += 1
        if call_count["count"] == 1:
            raise requests.exceptions.ConnectionError("boom")
        return _FakeResponse(_quotes_payload([{"symbol": "AAA", "shortname": "AAA Inc"}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    with pytest.raises(UpstreamFetchError):
        yahoo_search_client.search_assets("aaa")

    yahoo_search_client.search_assets("aaa")

    assert call_count["count"] == 2


def test_non_2xx_raises_upstream_fetch_error(monkeypatch):
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(status_code=500))

    with pytest.raises(UpstreamFetchError):
        yahoo_search_client.search_assets("aaa")


def test_429_raises_upstream_fetch_error(monkeypatch):
    monkeypatch.setattr(requests, "get", lambda *a, **k: _FakeResponse(status_code=429))

    with pytest.raises(UpstreamFetchError):
        yahoo_search_client.search_assets("aaa")


def test_network_error_raises_upstream_fetch_error(monkeypatch):
    def _raise(*args, **kwargs):
        raise requests.exceptions.ConnectionError("boom")

    monkeypatch.setattr(requests, "get", _raise)

    with pytest.raises(UpstreamFetchError):
        yahoo_search_client.search_assets("aaa")


def test_unparseable_json_raises_upstream_fetch_error(monkeypatch):
    class _BadJsonResponse(_FakeResponse):
        def json(self):
            raise ValueError("not json")

    monkeypatch.setattr(requests, "get", lambda *a, **k: _BadJsonResponse({}))

    with pytest.raises(UpstreamFetchError):
        yahoo_search_client.search_assets("aaa")


def test_sends_browser_user_agent_and_explicit_timeout(monkeypatch):
    captured = {}

    def _fake_get(url, params=None, headers=None, timeout=None):
        captured["url"] = url
        captured["params"] = params
        captured["headers"] = headers
        captured["timeout"] = timeout
        return _FakeResponse(_quotes_payload([]))

    monkeypatch.setattr(requests, "get", _fake_get)

    yahoo_search_client.search_assets("aaa")

    assert captured["url"] == yahoo_search_client._SEARCH_URL
    assert captured["params"]["q"] == "aaa"
    assert "Mozilla" in captured["headers"]["User-Agent"]
    assert captured["timeout"] is not None
