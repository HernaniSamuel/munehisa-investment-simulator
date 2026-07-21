"""Live test that hits the real BCB SGS API over the network.

Excluded from the default `pytest` run (see `addopts` in pyproject.toml). Run explicitly
with `pytest -m live` when you want to confirm the real integration still works - e.g.
after bumping the python-bcb version, or before a release.
"""

from datetime import date

import pytest

from data_service.services.bcb_inflation_client import fetch_brl_inflation

pytestmark = pytest.mark.live


def test_fetch_brl_inflation_returns_real_data_starting_1980():
    result = fetch_brl_inflation()

    assert result.start_date == date(1980, 1, 1)
    assert result.monthly_data
    first = result.monthly_data[0]
    assert first.date == date(1980, 1, 1)
    assert first.rate > 0
