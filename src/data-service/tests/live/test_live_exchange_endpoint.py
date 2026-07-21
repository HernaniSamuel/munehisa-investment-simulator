"""Live tests that hit the real yfinance/Yahoo Finance API over the network.

Excluded from the default `pytest` run (see `addopts` in pyproject.toml). Run explicitly
with `pytest -m live` when you want to confirm the real integration still works - e.g.
after bumping the yfinance version, or before a release.
"""

import pytest

from data_service.services.yfinance_exchange_client import fetch_exchange_rate

pytestmark = pytest.mark.live


def test_fetch_exchange_rate_returns_real_data_for_known_pair():
    result = fetch_exchange_rate("USD", "BRL")

    assert result.symbol == "USDBRL=X"
    assert result.from_currency == "USD"
    assert result.to_currency == "BRL"
    assert result.monthly_data
    first = result.monthly_data[0]
    assert first.open > 0
    assert first.high >= first.low
