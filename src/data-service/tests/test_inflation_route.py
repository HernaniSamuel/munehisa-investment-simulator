from datetime import date
from decimal import Decimal

from fastapi.testclient import TestClient

from data_service.exceptions import UpstreamFetchError
from data_service.main import app
from data_service.routes import inflation as inflation_route
from data_service.schemas.inflation import InflationMonthlyDataPoint, InflationResponse

_SAMPLE_INFLATION = InflationResponse(
    start_date=date(1980, 1, 1),
    monthly_data=[
        InflationMonthlyDataPoint(date=date(1980, 1, 1), rate=Decimal("6.62")),
        InflationMonthlyDataPoint(date=date(1980, 2, 1), rate=Decimal("4.62")),
    ],
)


def test_get_brl_inflation_returns_200_with_valid_key(client, auth_headers, monkeypatch):
    monkeypatch.setattr(inflation_route, "fetch_brl_inflation", lambda: _SAMPLE_INFLATION)

    response = client.get("/inflation/brl", headers=auth_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["start_date"] == "1980-01-01"
    assert body["monthly_data"][0]["rate"] == "6.62"


def test_get_brl_inflation_without_api_key_returns_401(client):
    response = client.get("/inflation/brl")

    assert response.status_code == 401
    assert response.json() == {"status": "UNAUTHORIZED", "message": "Invalid or missing API key."}


def test_get_brl_inflation_with_wrong_api_key_returns_401(client):
    response = client.get("/inflation/brl", headers={"X-API-Key": "wrong-key"})

    assert response.status_code == 401


def test_get_brl_inflation_upstream_error_returns_502(client, auth_headers, monkeypatch):
    def _raise_upstream():
        raise UpstreamFetchError("BCB SGS API is unreachable")

    monkeypatch.setattr(inflation_route, "fetch_brl_inflation", _raise_upstream)

    response = client.get("/inflation/brl", headers=auth_headers)

    assert response.status_code == 502
    assert response.json()["status"] == "BAD_GATEWAY"


def test_get_brl_inflation_unexpected_error_returns_500_with_error_shape(
    auth_headers, monkeypatch
):
    def _raise_bug():
        raise RuntimeError("something we didn't anticipate")

    monkeypatch.setattr(inflation_route, "fetch_brl_inflation", _raise_bug)

    # See the equivalent asset-route test for why raise_server_exceptions=False is used
    # here: it reflects what the client actually receives, not the process-local re-raise.
    non_raising_client = TestClient(app, raise_server_exceptions=False)

    response = non_raising_client.get("/inflation/brl", headers=auth_headers)

    assert response.status_code == 500
    assert response.json() == {
        "status": "INTERNAL_SERVER_ERROR",
        "message": "Internal server error.",
    }
