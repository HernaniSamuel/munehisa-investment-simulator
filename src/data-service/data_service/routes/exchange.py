from fastapi import APIRouter, Depends

from data_service.schemas.error import ErrorResponse
from data_service.schemas.exchange import ExchangeRateResponse
from data_service.security import require_api_key
from data_service.services.yfinance_exchange_client import fetch_exchange_rate

router = APIRouter(
    prefix="/exchange",
    tags=["Exchange"],
    dependencies=[Depends(require_api_key)],
)


@router.get(
    "/{from_currency}/{to_currency}",
    response_model=ExchangeRateResponse,
    responses={
        401: {"model": ErrorResponse, "description": "Missing or invalid API key"},
        404: {"model": ErrorResponse, "description": "Unknown currency pair"},
        502: {"model": ErrorResponse, "description": "Upstream data source unavailable"},
    },
)
def get_exchange_rate(from_currency: str, to_currency: str) -> ExchangeRateResponse:
    """Historical monthly OHLC exchange-rate series for a currency pair, sourced from
    Yahoo Finance via yfinance. `from_currency`/`to_currency` are 3-letter ISO 4217
    codes."""
    return fetch_exchange_rate(from_currency, to_currency)
