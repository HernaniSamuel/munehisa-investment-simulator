from datetime import date
from decimal import Decimal

import pytest
import yfinance

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.services import yfinance_exchange_client
from tests.fixtures.yfinance_history import build_history_df, empty_history_df


class _StaticTicker:
    def __init__(self, history_df):
        self._history_df = history_df

    def history(self, **kwargs):
        return self._history_df


class _RaisingTicker:
    def __init__(self, exc: Exception):
        self._exc = exc

    def history(self, **kwargs):
        raise self._exc


def _patch_tickers(monkeypatch, by_symbol: dict) -> None:
    """Route yf.Ticker(symbol) to a canned DataFrame or a raised exception, per symbol.

    `by_symbol` maps symbol -> DataFrame (wrapped in _StaticTicker) or Exception
    (wrapped in _RaisingTicker). Mirrors `yfinance_exchange_client.yf` being the same
    module object as `yfinance` (import yfinance as yf).
    """

    def _ticker(symbol):
        entry = by_symbol[symbol]
        if isinstance(entry, Exception):
            return _RaisingTicker(entry)
        return _StaticTicker(entry)

    monkeypatch.setattr(yfinance, "Ticker", _ticker)


def test_fetch_exchange_rate_direct_symbol_success(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 5.0,
                "high": 5.2,
                "low": 4.9,
                "close": 5.1,
                "volume": 0,
            }
        ]
    )
    _patch_tickers(monkeypatch, {"USDBRL=X": hist})

    result = yfinance_exchange_client.fetch_exchange_rate("usd", "brl")

    assert result.symbol == "USDBRL=X"
    assert result.from_currency == "USD"
    assert result.to_currency == "BRL"
    assert result.start_date == date(2024, 1, 1)
    assert len(result.monthly_data) == 1

    point = result.monthly_data[0]
    assert point.date == date(2024, 1, 1)
    assert point.open == Decimal("5.0")
    assert point.high == Decimal("5.2")
    assert point.low == Decimal("4.9")
    assert point.close == Decimal("5.1")


def test_fetch_exchange_rate_inverse_symbol_fallback_inverts_rates(monkeypatch):
    # Direct symbol (USDBRL=X) has no data; BRLUSD=X does, so the result is derived from
    # it and inverted. High/low swap because inverting a ratio flips which side is
    # larger - all values chosen as exact binary fractions so the divisions are exact
    # and the assertions aren't tautological with the implementation.
    inverse_hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 0.25,
                "high": 0.5,
                "low": 0.125,
                "close": 0.25,
                "volume": 0,
            }
        ]
    )
    _patch_tickers(
        monkeypatch, {"USDBRL=X": empty_history_df(), "BRLUSD=X": inverse_hist}
    )

    result = yfinance_exchange_client.fetch_exchange_rate("USD", "BRL")

    # The response always reports the canonical direct-format symbol, regardless of
    # which one was actually queried.
    assert result.symbol == "USDBRL=X"
    point = result.monthly_data[0]
    assert point.open == Decimal("4.0")
    assert point.high == Decimal("8.0")
    assert point.low == Decimal("2.0")
    assert point.close == Decimal("4.0")


def test_fetch_exchange_rate_same_currency_returns_rate_of_one(monkeypatch):
    # Must never reach yfinance at all: yf.Ticker("USDUSD=X").history(period="max")
    # raises outright (period='max' invalid for that symbol), so this is special-cased
    # before ever calling yf.Ticker.
    def _fail_ticker(symbol):
        raise AssertionError("yf.Ticker should not be called for from == to")

    monkeypatch.setattr(yfinance, "Ticker", _fail_ticker)

    result = yfinance_exchange_client.fetch_exchange_rate("usd", "usd")

    assert result.symbol == "USDUSD=X"
    assert result.from_currency == "USD"
    assert result.to_currency == "USD"
    assert len(result.monthly_data) == 1
    point = result.monthly_data[0]
    assert point.date == date(1970, 1, 1)
    assert point.open == point.high == point.low == point.close == Decimal(1)


def test_fetch_exchange_rate_raises_not_found_when_both_symbols_empty(monkeypatch):
    _patch_tickers(
        monkeypatch,
        {"USDNOTACURRENCY=X": empty_history_df(), "NOTACURRENCYUSD=X": empty_history_df()},
    )

    with pytest.raises(AssetNotFoundError):
        yfinance_exchange_client.fetch_exchange_rate("usd", "notacurrency")


def test_fetch_exchange_rate_raises_upstream_error_on_direct_fetch_failure(monkeypatch):
    _patch_tickers(monkeypatch, {"USDBRL=X": ConnectionError("boom")})

    with pytest.raises(UpstreamFetchError):
        yfinance_exchange_client.fetch_exchange_rate("USD", "BRL")


def test_fetch_exchange_rate_raises_upstream_error_on_inverse_fetch_failure(monkeypatch):
    _patch_tickers(
        monkeypatch, {"USDBRL=X": empty_history_df(), "BRLUSD=X": ConnectionError("boom")}
    )

    with pytest.raises(UpstreamFetchError):
        yfinance_exchange_client.fetch_exchange_rate("USD", "BRL")


def test_fetch_exchange_rate_forward_fills_gap_month(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 5.0,
                "high": 5.2,
                "low": 4.9,
                "close": 5.1,
                "volume": 0,
            },
            {
                "date": "2024-03-01",
                "open": 6.0,
                "high": 6.2,
                "low": 5.9,
                "close": 6.1,
                "volume": 0,
            },
        ]
    )
    _patch_tickers(monkeypatch, {"USDBRL=X": hist})

    result = yfinance_exchange_client.fetch_exchange_rate("USD", "BRL")

    assert [p.date for p in result.monthly_data] == [
        date(2024, 1, 1),
        date(2024, 2, 1),
        date(2024, 3, 1),
    ]
    jan, feb, march = result.monthly_data
    assert feb.open == jan.open
    assert feb.high == jan.high
    assert feb.low == jan.low
    assert feb.close == jan.close
    assert march.open == Decimal("6.0")


def test_fetch_exchange_rate_preserves_high_precision_without_quantization(monkeypatch):
    # Real precision varies by pair and doesn't follow a fixed decimal convention -
    # nothing should round it away.
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 5.09119987487793,
                "high": 5.09119987487793,
                "low": 5.09119987487793,
                "close": 5.09119987487793,
                "volume": 0,
            }
        ]
    )
    _patch_tickers(monkeypatch, {"USDBRL=X": hist})

    point = yfinance_exchange_client.fetch_exchange_rate("USD", "BRL").monthly_data[0]

    assert point.close == Decimal("5.09119987487793")
