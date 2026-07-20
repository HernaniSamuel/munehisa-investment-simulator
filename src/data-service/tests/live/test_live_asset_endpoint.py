"""Live tests that hit the real yfinance/Yahoo Finance API over the network.

Excluded from the default `pytest` run (see `addopts` in pyproject.toml). Run explicitly
with `pytest -m live` when you want to confirm the real integration still works - e.g.
after bumping the yfinance version, or before a release.
"""

import pytest

from data_service.services.yfinance_client import fetch_asset

pytestmark = pytest.mark.live


def test_fetch_asset_returns_real_data_for_known_ticker():
    result = fetch_asset("AAPL")

    assert result.ticker == "AAPL"
    assert result.name
    assert result.base_currency
    assert result.monthly_data
    first = result.monthly_data[0]
    assert first.open > 0
    assert first.high >= first.low
