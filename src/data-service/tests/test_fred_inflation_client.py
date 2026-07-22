from datetime import date
from decimal import Decimal

import pytest
import requests

from data_service.exceptions import UpstreamFetchError
from data_service.services import fred_inflation_client
from tests.fixtures.fred_cpi import build_cpi_csv, empty_cpi_csv, malformed_cpi_csv


class _FakeResponse:
    def __init__(self, text: str, status_code: int = 200):
        self.text = text
        self.status_code = status_code

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise requests.exceptions.HTTPError(f"{self.status_code} error")


def test_fetch_usd_inflation_returns_full_series(monkeypatch):
    csv_text = build_cpi_csv(
        [
            {"date": "1947-01-01", "value": 21.48},
            {"date": "1947-02-01", "value": 21.62},
        ]
    )
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(csv_text))

    result = fred_inflation_client.fetch_usd_inflation()

    assert result.start_date == date(1947, 1, 1)
    assert len(result.monthly_data) == 2
    first, second = result.monthly_data
    assert first.date == date(1947, 1, 1)
    assert first.value == Decimal("21.48")
    assert second.date == date(1947, 2, 1)
    assert second.value == Decimal("21.62")


def test_fetch_usd_inflation_accepts_legacy_date_column_name(monkeypatch):
    # Regression test: MineInvest defensively accepts both 'date' and
    # 'observation_date' as the header name, implying FRED renamed it at some point -
    # this must keep working if FRED ever reverts or renames again.
    csv_text = build_cpi_csv([{"date": "1947-01-01", "value": 21.48}], date_column="date")
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(csv_text))

    result = fred_inflation_client.fetch_usd_inflation()

    assert result.monthly_data[0].date == date(1947, 1, 1)


def test_fetch_usd_inflation_uses_real_fred_column_names(monkeypatch):
    # Confirmed live column names are exactly 'observation_date' and 'CPIAUCSL' -
    # asserted against a literal CSV string here, not build_cpi_csv's default, so a
    # fixture drift can't silently stop testing the real shape.
    csv_text = "observation_date,CPIAUCSL\n1947-01-01,21.48\n"
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(csv_text))

    result = fred_inflation_client.fetch_usd_inflation()

    assert result.monthly_data[0].value == Decimal("21.48")


def test_fetch_usd_inflation_drops_mid_series_gap_without_forward_filling(monkeypatch):
    # Regression test for the real October 2025 gap (US government shutdown delaying
    # BLS publication): a '.'-valued row must be dropped entirely, not fabricated by
    # carrying the previous month's value forward.
    csv_text = build_cpi_csv(
        [
            {"date": "2025-09-01", "value": 300.0},
            {"date": "2025-10-01", "value": None},
            {"date": "2025-11-01", "value": 301.0},
        ]
    )
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(csv_text))

    result = fred_inflation_client.fetch_usd_inflation()

    dates = [point.date for point in result.monthly_data]
    assert dates == [date(2025, 9, 1), date(2025, 11, 1)]


def test_fetch_usd_inflation_passes_the_series_id_and_an_explicit_timeout(monkeypatch):
    captured = {}

    def _fake_get(url, timeout=None):
        captured["url"] = url
        captured["timeout"] = timeout
        return _FakeResponse(build_cpi_csv([{"date": "1947-01-01", "value": 21.48}]))

    monkeypatch.setattr(requests, "get", _fake_get)

    fred_inflation_client.fetch_usd_inflation()

    assert "CPIAUCSL" in captured["url"]
    # Regression test: requests has no implicit timeout at all - an unbounded call must
    # not be left to hang indefinitely against an unreachable/slow FRED.
    assert captured["timeout"] is not None


def test_fetch_usd_inflation_wraps_connection_error_as_upstream_fetch_error(monkeypatch):
    def _raise(url, timeout=None):
        raise requests.exceptions.ConnectionError("boom")

    monkeypatch.setattr(requests, "get", _raise)

    with pytest.raises(UpstreamFetchError):
        fred_inflation_client.fetch_usd_inflation()


def test_fetch_usd_inflation_wraps_bad_http_status_as_upstream_fetch_error(monkeypatch):
    monkeypatch.setattr(
        requests, "get", lambda url, timeout=None: _FakeResponse("", status_code=500)
    )

    with pytest.raises(UpstreamFetchError):
        fred_inflation_client.fetch_usd_inflation()


def test_fetch_usd_inflation_raises_upstream_error_on_empty_result(monkeypatch):
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(empty_cpi_csv()))

    with pytest.raises(UpstreamFetchError):
        fred_inflation_client.fetch_usd_inflation()


def test_fetch_usd_inflation_raises_upstream_error_when_no_recognized_date_column(monkeypatch):
    # Regression test: if FRED renames the date column to something other than
    # 'observation_date'/'date', this must surface as a 502, not an unhandled KeyError.
    monkeypatch.setattr(
        requests, "get", lambda url, timeout=None: _FakeResponse(malformed_cpi_csv())
    )

    with pytest.raises(UpstreamFetchError):
        fred_inflation_client.fetch_usd_inflation()


def test_fetch_usd_inflation_preserves_decimal_precision_without_float_artifacts(monkeypatch):
    csv_text = build_cpi_csv([{"date": "1947-01-01", "value": 0.16}])
    monkeypatch.setattr(requests, "get", lambda url, timeout=None: _FakeResponse(csv_text))

    result = fred_inflation_client.fetch_usd_inflation()

    assert result.monthly_data[0].value == Decimal("0.16")
