from datetime import date
from decimal import Decimal

import pytest
import yfinance

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.services import yfinance_client
from tests.fixtures.yfinance_history import build_history_df, empty_history_df


class _FakeTicker:
    def __init__(self, history_df, info=None):
        self._history_df = history_df
        self.info = info if info is not None else {"longName": "Fake Corp", "currency": "USD"}

    def history(self, **kwargs):
        return self._history_df


class _BrokenTicker:
    @property
    def info(self):
        raise ConnectionError("boom")

    def history(self, **kwargs):
        raise ConnectionError("boom")


def _patch_ticker(monkeypatch, fake_ticker) -> None:
    # `yfinance_client.yf` is the same module object as `yfinance` (import yfinance as yf),
    # so patching it here is equivalent to patching the attribute the service code sees.
    monkeypatch.setattr(yfinance, "Ticker", lambda ticker: fake_ticker)


def test_fetch_asset_basic_shape(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 100.0,
                "high": 105.0,
                "low": 99.0,
                "close": 104.0,
                "volume": 1_000_000,
            }
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    result = yfinance_client.fetch_asset("fake")

    assert result.ticker == "FAKE"
    assert result.name == "Fake Corp"
    assert result.base_currency == "USD"
    assert result.start_date == date(2024, 1, 1)
    assert len(result.monthly_data) == 1

    point = result.monthly_data[0]
    assert point.date == date(2024, 1, 1)
    assert point.open == Decimal("100.00")
    assert point.close == Decimal("104.00")
    assert point.volume == 1_000_000
    assert point.dividends is None
    assert point.splits is None


def test_fetch_asset_forward_fills_gap_month(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 100.0,
                "high": 110.0,
                "low": 95.0,
                "close": 108.0,
                "volume": 500,
            },
            {
                "date": "2024-03-01",
                "open": 200.0,
                "high": 210.0,
                "low": 195.0,
                "close": 205.0,
                "volume": 700,
            },
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    result = yfinance_client.fetch_asset("gap")

    assert [p.date for p in result.monthly_data] == [
        date(2024, 1, 1),
        date(2024, 2, 1),
        date(2024, 3, 1),
    ]

    jan, feb, march = result.monthly_data
    # February had zero trading days: OHLC is forward-filled from January, but volume
    # is left at 0 rather than carrying January's volume over.
    assert feb.open == jan.open
    assert feb.high == jan.high
    assert feb.low == jan.low
    assert feb.close == jan.close
    assert feb.volume == 0
    assert feb.dividends is None
    assert feb.splits is None
    assert march.open == Decimal("200.00")
    assert march.volume == 700


def test_fetch_asset_captures_dividends_and_splits(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 100.0,
                "high": 100.0,
                "low": 100.0,
                "close": 100.0,
                "volume": 10,
                "dividends": 0.5,
            },
            {
                "date": "2024-02-01",
                "open": 50.0,
                "high": 55.0,
                "low": 49.0,
                "close": 52.0,
                "volume": 20,
                "splits": 2.0,
            },
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    result = yfinance_client.fetch_asset("split")

    jan, feb = result.monthly_data
    assert jan.dividends == Decimal("0.5")
    assert jan.splits is None
    assert feb.dividends is None
    assert feb.splits == Decimal("2.0")


def test_fetch_asset_no_split_across_many_days_stays_none(monkeypatch):
    # Regression test: yfinance's daily "Stock Splits" column uses 0.0 (not 1.0) as the
    # no-event fill value. Aggregating a month's worth of 0.0s with 'prod' collapses to
    # 0.0 - and 0.0 != 1.0, so it would be misreported as a split every single month.
    # 'sum' + a `> 0` check (same convention as dividends) is what's actually correct.
    hist = build_history_df(
        [
            {"date": d, "open": 10.0, "high": 10.0, "low": 10.0, "close": 10.0, "volume": 1}
            for d in ["2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    point = yfinance_client.fetch_asset("nosplit").monthly_data[0]

    assert point.splits is None


def test_fetch_asset_quantizes_ohlc_to_two_decimals(monkeypatch):
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 123.456,
                "high": 130.001,
                "low": 120.004,
                "close": 125.0,
                "volume": 1,
            }
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    point = yfinance_client.fetch_asset("round").monthly_data[0]

    assert point.open == Decimal("123.46")
    assert point.high == Decimal("130.00")
    assert point.low == Decimal("120.00")


def test_fetch_asset_rounds_exact_half_cent_up(monkeypatch):
    # Money uses ROUND_HALF_UP (commercial rounding), not Python decimal's default
    # ROUND_HALF_EVEN (banker's rounding) - an exact tie always rounds up, regardless of
    # whether the resulting cent is even or odd.
    hist = build_history_df(
        [
            {
                "date": "2024-01-02",
                "open": 125.005,
                "high": 125.005,
                "low": 120.005,
                "close": 130.005,
                "volume": 1,
            }
        ]
    )
    _patch_ticker(monkeypatch, _FakeTicker(hist))

    point = yfinance_client.fetch_asset("halfup").monthly_data[0]

    assert point.open == Decimal("125.01")
    assert point.low == Decimal("120.01")
    assert point.close == Decimal("130.01")


def test_fetch_asset_raises_not_found_for_empty_history(monkeypatch):
    _patch_ticker(monkeypatch, _FakeTicker(empty_history_df()))

    with pytest.raises(AssetNotFoundError):
        yfinance_client.fetch_asset("NOPE")


def test_fetch_asset_raises_upstream_error_on_fetch_failure(monkeypatch):
    monkeypatch.setattr(yfinance, "Ticker", lambda ticker: _BrokenTicker())

    with pytest.raises(UpstreamFetchError):
        yfinance_client.fetch_asset("BROKEN")
