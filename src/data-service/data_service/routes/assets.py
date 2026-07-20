from fastapi import APIRouter, Depends

from data_service.schemas.asset import AssetResponse
from data_service.schemas.error import ErrorResponse
from data_service.security import require_api_key
from data_service.services.yfinance_client import fetch_asset

router = APIRouter(
    prefix="/assets",
    tags=["Assets"],
    dependencies=[Depends(require_api_key)],
)


@router.get(
    "/{ticker}",
    response_model=AssetResponse,
    responses={
        401: {"model": ErrorResponse, "description": "Missing or invalid API key"},
        404: {"model": ErrorResponse, "description": "Unknown ticker"},
        502: {"model": ErrorResponse, "description": "Upstream data source unavailable"},
    },
)
def get_asset(ticker: str) -> AssetResponse:
    """Historical monthly OHLCV series (+ dividends/splits) for a ticker, sourced from
    Yahoo Finance via yfinance."""
    return fetch_asset(ticker)
