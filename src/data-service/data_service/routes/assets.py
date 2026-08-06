from typing import Annotated

from fastapi import APIRouter, Depends, Query

from data_service.schemas.asset import AssetResponse, AssetSearchResponse
from data_service.schemas.error import ErrorResponse
from data_service.security import require_api_key
from data_service.services.yahoo_search_client import search_assets
from data_service.services.yfinance_client import fetch_asset

router = APIRouter(
    prefix="/assets",
    tags=["Assets"],
    dependencies=[Depends(require_api_key)],
)


# Declared before "/{ticker}" deliberately: FastAPI matches routes in declaration order,
# and "/{ticker}" is a catch-all that would otherwise swallow this as ticker="search".
@router.get(
    "/search",
    response_model=AssetSearchResponse,
    responses={
        401: {"model": ErrorResponse, "description": "Missing or invalid API key"},
        422: {
            "model": ErrorResponse,
            "description": "`q` is shorter than 2 or longer than 50 characters",
        },
        502: {"model": ErrorResponse, "description": "Upstream data source unavailable"},
    },
)
def get_asset_search(
    q: Annotated[
        str,
        Query(min_length=2, max_length=50, description="Partial ticker symbol or company name"),
    ],
) -> AssetSearchResponse:
    """Tradeable tickers matching a partial symbol or company name, sourced from Yahoo
    Finance's public search endpoint. Capped at 15 results; identical queries within five
    minutes are served from an in-memory cache."""
    return search_assets(q)


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
