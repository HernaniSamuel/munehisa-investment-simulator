from datetime import date
from decimal import Decimal

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.routes import assets as assets_route
from data_service.schemas.asset import AssetResponse, MonthlyDataPoint

_SAMPLE_ASSET = AssetResponse(
    ticker="AAPL",
    name="Apple Inc.",
    base_currency="USD",
    start_date=date(2024, 1, 1),
    monthly_data=[
        MonthlyDataPoint(
            date=date(2024, 1, 1),
            open=Decimal("100.00"),
            high=Decimal("110.00"),
            low=Decimal("95.00"),
            close=Decimal("108.00"),
            volume=1000,
        )
    ],
)


def test_get_asset_returns_200_with_valid_key(client, auth_headers, monkeypatch):
    monkeypatch.setattr(assets_route, "fetch_asset", lambda ticker: _SAMPLE_ASSET)

    response = client.get("/assets/AAPL", headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["ticker"] == "AAPL"
    assert body["base_currency"] == "USD"
    assert body["monthly_data"][0]["close"] == "108.00"


def test_get_asset_without_api_key_returns_401(client):
    response = client.get("/assets/AAPL")

    assert response.status_code == 401
    assert response.json() == {"status": "UNAUTHORIZED", "message": "Invalid or missing API key."}


def test_get_asset_with_wrong_api_key_returns_401(client):
    response = client.get("/assets/AAPL", headers={"X-API-Key": "wrong-key"})

    assert response.status_code == 401


def test_get_asset_unknown_ticker_returns_404(client, auth_headers, monkeypatch):
    def _raise_not_found(ticker):
        raise AssetNotFoundError(f"No data available for {ticker}")

    monkeypatch.setattr(assets_route, "fetch_asset", _raise_not_found)

    response = client.get("/assets/NOTATICKER", headers=auth_headers)

    assert response.status_code == 404
    body = response.json()
    assert body["status"] == "NOT_FOUND"


def test_get_asset_upstream_error_returns_502(client, auth_headers, monkeypatch):
    def _raise_upstream(ticker):
        raise UpstreamFetchError("yfinance is unreachable")

    monkeypatch.setattr(assets_route, "fetch_asset", _raise_upstream)

    response = client.get("/assets/AAPL", headers=auth_headers)

    assert response.status_code == 502
    assert response.json()["status"] == "BAD_GATEWAY"
