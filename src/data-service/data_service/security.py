import secrets

from fastapi import Depends, HTTPException, status
from fastapi.security import APIKeyHeader

from data_service.config import settings

_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


def require_api_key(api_key: str | None = Depends(_api_key_header)) -> None:
    if api_key is None or not secrets.compare_digest(api_key, settings.api_key):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing API key."
        )
