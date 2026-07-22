"""Live test that hits the real FRED CSV export endpoint over the network.

Excluded from the default `pytest` run (see `addopts` in pyproject.toml). Run explicitly
with `pytest -m live` when you want to confirm the real integration still works - e.g.
before a release.
"""

from datetime import date

import pytest

from data_service.services.fred_inflation_client import fetch_usd_inflation

pytestmark = pytest.mark.live


def test_fetch_usd_inflation_returns_real_data_starting_1947():
    result = fetch_usd_inflation()

    assert result.start_date == date(1947, 1, 1)
    assert result.monthly_data
    first = result.monthly_data[0]
    assert first.date == date(1947, 1, 1)
    assert first.value > 0
