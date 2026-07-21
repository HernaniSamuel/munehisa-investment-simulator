from datetime import date
from decimal import Decimal

import pytest
from bcb import sgs
from bcb.exceptions import SGSError

from data_service.exceptions import UpstreamFetchError
from data_service.services import bcb_inflation_client
from tests.fixtures.bcb_sgs import build_ipca_df, empty_ipca_df, malformed_ipca_df


def test_fetch_brl_inflation_returns_full_series(monkeypatch):
    df = build_ipca_df(
        [
            {"date": "1980-01-01", "rate": 6.62},
            {"date": "1980-02-01", "rate": 4.62},
        ]
    )
    monkeypatch.setattr(sgs, "get", lambda code, timeout=None: df)

    result = bcb_inflation_client.fetch_brl_inflation()

    assert result.start_date == date(1980, 1, 1)
    assert len(result.monthly_data) == 2
    first, second = result.monthly_data
    assert first.date == date(1980, 1, 1)
    assert first.rate == Decimal("6.62")
    assert second.date == date(1980, 2, 1)
    assert second.rate == Decimal("4.62")


def test_fetch_brl_inflation_does_not_leak_raw_bcb_column_name(monkeypatch):
    df = build_ipca_df([{"date": "1980-01-01", "rate": 6.62}])
    monkeypatch.setattr(sgs, "get", lambda code, timeout=None: df)

    result = bcb_inflation_client.fetch_brl_inflation()

    assert result.model_dump()["monthly_data"][0] == {
        "date": date(1980, 1, 1),
        "rate": Decimal("6.62"),
    }


def test_fetch_brl_inflation_passes_the_ipca_series_code_and_an_explicit_timeout(monkeypatch):
    captured = {}

    def _fake_get(code, timeout=None):
        captured["code"] = code
        captured["timeout"] = timeout
        return build_ipca_df([{"date": "1980-01-01", "rate": 1.0}])

    monkeypatch.setattr(sgs, "get", _fake_get)

    bcb_inflation_client.fetch_brl_inflation()

    assert captured["code"] == 433
    # Regression test: python-bcb's own default is no timeout at all, which took
    # several minutes to fail against a bad series code in practice - this must not be
    # left at that default.
    assert captured["timeout"] is not None


def test_fetch_brl_inflation_wraps_sgs_error_as_upstream_fetch_error(monkeypatch):
    def _raise(code, timeout=None):
        raise SGSError("... request failed: The read operation timed out")

    monkeypatch.setattr(sgs, "get", _raise)

    with pytest.raises(UpstreamFetchError):
        bcb_inflation_client.fetch_brl_inflation()


def test_fetch_brl_inflation_wraps_arbitrary_exception_as_upstream_fetch_error(monkeypatch):
    def _raise(code, timeout=None):
        raise ConnectionError("boom")

    monkeypatch.setattr(sgs, "get", _raise)

    with pytest.raises(UpstreamFetchError):
        bcb_inflation_client.fetch_brl_inflation()


def test_fetch_brl_inflation_raises_upstream_error_on_empty_result(monkeypatch):
    # The call succeeding but returning nothing usable is a distinct failure mode from
    # the call raising - same distinction the yfinance clients make via hist.empty.
    monkeypatch.setattr(sgs, "get", lambda code, timeout=None: empty_ipca_df())

    with pytest.raises(UpstreamFetchError):
        bcb_inflation_client.fetch_brl_inflation()


def test_fetch_brl_inflation_raises_upstream_error_when_expected_column_missing(monkeypatch):
    # Regression test: if a future python-bcb version renames the '433' column, the
    # KeyError from indexing into it must surface as a 502, not an unhandled 500.
    monkeypatch.setattr(sgs, "get", lambda code, timeout=None: malformed_ipca_df())

    with pytest.raises(UpstreamFetchError):
        bcb_inflation_client.fetch_brl_inflation()


def test_fetch_brl_inflation_preserves_decimal_precision_without_float_artifacts(monkeypatch):
    # Regression test: Decimal(str(v)) must be used, not Decimal(v) on the raw float64 -
    # same reasoning as the exchange/asset clients' decimal handling.
    df = build_ipca_df([{"date": "1980-01-01", "rate": 0.16}])
    monkeypatch.setattr(sgs, "get", lambda code, timeout=None: df)

    result = bcb_inflation_client.fetch_brl_inflation()

    assert result.monthly_data[0].rate == Decimal("0.16")
