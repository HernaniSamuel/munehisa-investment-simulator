"""Live tests that hit the real Yahoo Finance search API over the network.

Excluded from the default `pytest` run (see `addopts` in pyproject.toml). Run explicitly
with `pytest -m live` when you want to confirm the real integration still works - e.g.
after Yahoo changes its search response shape, or before a release.
"""

import pytest

from data_service.services.yahoo_search_client import search_assets

pytestmark = pytest.mark.live


def test_search_assets_finds_known_ticker_by_partial_name():
    response = search_assets("petrobras")

    assert response.results
    tickers = [result.ticker for result in response.results]
    assert any(ticker.endswith(".SA") for ticker in tickers)
