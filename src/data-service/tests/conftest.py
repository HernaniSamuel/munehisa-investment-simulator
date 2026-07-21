import os

os.environ.setdefault("DATA_SERVICE_API_KEY", "test-api-key")

import pytest
from fastapi.testclient import TestClient

from data_service.main import app

API_KEY = "test-api-key"


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def auth_headers() -> dict[str, str]:
    return {"X-API-Key": API_KEY}
