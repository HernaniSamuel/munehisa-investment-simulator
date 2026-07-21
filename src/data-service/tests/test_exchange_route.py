from datetime import date
from decimal import Decimal

from fastapi.testclient import TestClient

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.main import app
from data_service.routes import exchange as exchange_route
from data_service.schemas.exchange import ExchangeMonthlyDataPoint, ExchangeRateResponse

_SAMPLE_RATE = ExchangeRateResponse(
    symbol="USDBRL=X",
    from_currency="USD",
    to_currency="BRL",
    start_date=date(2024, 1, 1),
    monthly_data=[
        ExchangeMonthlyDataPoint(
            date=date(2024, 1, 1),
            open=Decimal("5.0"),
            high=Decimal("5.2"),
            low=Decimal("4.9"),
            close=Decimal("5.1"),
        )
    ],
)


def test_get_exchange_rate_returns_200_with_valid_key(client, auth_headers, monkeypatch):
    monkeypatch.setattr(
        exchange_route, "fetch_exchange_rate", lambda from_currency, to_currency: _SAMPLE_RATE
    )

    response = client.get("/exchange/USD/BRL", headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["symbol"] == "USDBRL=X"
    assert body["monthly_data"][0]["close"] == "5.1"


def test_get_exchange_rate_without_api_key_returns_401(client):
    response = client.get("/exchange/USD/BRL")

    assert response.status_code == 401
    assert response.json() == {"status": "UNAUTHORIZED", "message": "Invalid or missing API key."}


def test_get_exchange_rate_with_wrong_api_key_returns_401(client):
    response = client.get("/exchange/USD/BRL", headers={"X-API-Key": "wrong-key"})

    assert response.status_code == 401


def test_get_exchange_rate_unknown_pair_returns_404(client, auth_headers, monkeypatch):
    def _raise_not_found(from_currency, to_currency):
        raise AssetNotFoundError(f"No exchange rate data found for {from_currency}/{to_currency}")

    monkeypatch.setattr(exchange_route, "fetch_exchange_rate", _raise_not_found)

    response = client.get("/exchange/USD/NOTACURRENCY", headers=auth_headers)

    assert response.status_code == 404
    assert response.json()["status"] == "NOT_FOUND"


def test_get_exchange_rate_upstream_error_returns_502(client, auth_headers, monkeypatch):
    def _raise_upstream(from_currency, to_currency):
        raise UpstreamFetchError("yfinance is unreachable")

    monkeypatch.setattr(exchange_route, "fetch_exchange_rate", _raise_upstream)

    response = client.get("/exchange/USD/BRL", headers=auth_headers)

    assert response.status_code == 502
    assert response.json()["status"] == "BAD_GATEWAY"


def test_get_exchange_rate_unexpected_error_returns_500_with_error_shape(
    auth_headers, monkeypatch
):
    def _raise_bug(from_currency, to_currency):
        raise RuntimeError("something we didn't anticipate")

    monkeypatch.setattr(exchange_route, "fetch_exchange_rate", _raise_bug)

    # See the equivalent asset-route test for why raise_server_exceptions=False is used
    # here: it reflects what the client actually receives, not the process-local re-raise.
    non_raising_client = TestClient(app, raise_server_exceptions=False)

    response = non_raising_client.get("/exchange/USD/BRL", headers=auth_headers)

    assert response.status_code == 500
    assert response.json() == {
        "status": "INTERNAL_SERVER_ERROR",
        "message": "Internal server error.",
    }
