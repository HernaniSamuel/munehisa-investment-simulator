import io
import json
import logging
from datetime import date
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient

from data_service import main as main_module
from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.main import app
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
    prices_split_adjusted=True,
)


@pytest.fixture
def log_lines():
    """Captures every JSON log line data_service.main emits during a test, using the
    same JsonFormatter/request-id filter as production - mirrors the Java backend's
    ListAppender-based logging tests (RestExceptionHandlerTest)."""
    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(main_module._handler.formatter)
    handler.addFilter(main_module._RequestIdLogFilter())
    root = logging.getLogger()
    root.addHandler(handler)
    try:
        yield stream
    finally:
        root.removeHandler(handler)


def _own_lines(stream: io.StringIO) -> list[dict]:
    parsed = [json.loads(line) for line in stream.getvalue().splitlines() if line.strip()]
    return [line for line in parsed if line.get("logger") == "data_service.main"]


def test_log_output_is_valid_json_with_required_fields(client, log_lines):
    response = client.get("/health")

    assert response.status_code == 200
    lines = _own_lines(log_lines)
    assert lines, "expected at least one JSON log line"
    for line in lines:
        assert "timestamp" in line
        assert "level" in line
        assert "logger" in line
        assert "message" in line


def test_every_request_produces_one_access_log_line_with_required_fields(
    client, auth_headers, monkeypatch, log_lines
):
    monkeypatch.setattr(assets_route, "fetch_asset", lambda ticker: _SAMPLE_ASSET)

    response = client.get("/assets/AAPL", headers=auth_headers)

    assert response.status_code == 200
    lines = _own_lines(log_lines)
    access_lines = [line for line in lines if line.get("message") == "request completed"]
    assert len(access_lines) == 1
    access_line = access_lines[0]
    assert access_line["method"] == "GET"
    assert access_line["path"] == "/assets/AAPL"
    assert access_line["status"] == 200
    assert isinstance(access_line["durationMs"], int)
    assert access_line["requestId"]


def test_404_response_logs_at_warn_with_shared_request_id(
    client, auth_headers, monkeypatch, log_lines
):
    def _raise_not_found(ticker):
        raise AssetNotFoundError(f"No data available for {ticker}")

    monkeypatch.setattr(assets_route, "fetch_asset", _raise_not_found)

    response = client.get("/assets/NOTATICKER", headers=auth_headers)

    assert response.status_code == 404
    lines = _own_lines(log_lines)
    access_line = next(line for line in lines if line.get("message") == "request completed")
    warn_line = next(line for line in lines if line.get("level") == "WARNING")
    assert "AssetNotFoundError" in warn_line["message"]
    assert access_line["requestId"] == warn_line["requestId"]


def test_502_response_logs_at_error_with_shared_request_id(
    client, auth_headers, monkeypatch, log_lines
):
    def _raise_upstream(ticker):
        raise UpstreamFetchError("yfinance is unreachable")

    monkeypatch.setattr(assets_route, "fetch_asset", _raise_upstream)

    response = client.get("/assets/AAPL", headers=auth_headers)

    assert response.status_code == 502
    lines = _own_lines(log_lines)
    access_line = next(line for line in lines if line.get("message") == "request completed")
    error_line = next(line for line in lines if line.get("level") == "ERROR")
    assert "UpstreamFetchError" in error_line["message"]
    assert access_line["requestId"] == error_line["requestId"]


def test_401_response_logs_at_warn(client, log_lines):
    response = client.get("/assets/AAPL")

    assert response.status_code == 401
    lines = _own_lines(log_lines)
    access_line = next(line for line in lines if line.get("message") == "request completed")
    warn_lines = [line for line in lines if line.get("level") == "WARNING"]
    assert len(warn_lines) == 1
    assert "HTTPException" in warn_lines[0]["message"]
    # the submitted (missing/wrong) API key must never reach the log line
    assert "X-API-Key" not in warn_lines[0]["message"]
    assert access_line["requestId"] == warn_lines[0]["requestId"]


def test_422_response_logs_at_warn(client, auth_headers, log_lines):
    response = client.get("/assets/search", params={"q": "a"}, headers=auth_headers)

    assert response.status_code == 422
    lines = _own_lines(log_lines)
    access_line = next(line for line in lines if line.get("message") == "request completed")
    warn_lines = [line for line in lines if line.get("level") == "WARNING"]
    assert len(warn_lines) == 1
    assert warn_lines[0]["message"].startswith("validation error:")
    assert access_line["requestId"] == warn_lines[0]["requestId"]


def test_unhandled_exception_logs_at_error_with_shared_request_id(
    auth_headers, monkeypatch, log_lines
):
    def _raise_bug(ticker):
        raise RuntimeError("something we didn't anticipate")

    monkeypatch.setattr(assets_route, "fetch_asset", _raise_bug)

    # See test_assets_route.py's test_get_asset_unexpected_error_returns_500_with_error_shape
    # for why raise_server_exceptions=False is needed here.
    non_raising_client = TestClient(app, raise_server_exceptions=False)

    response = non_raising_client.get("/assets/AAPL", headers=auth_headers)

    assert response.status_code == 500
    lines = _own_lines(log_lines)
    access_line = next(line for line in lines if line.get("message") == "request completed")
    error_line = next(line for line in lines if line.get("level") == "ERROR")
    assert "exc_info" in error_line
    assert access_line["requestId"] == error_line["requestId"]
