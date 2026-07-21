from fastapi import APIRouter, Depends

from data_service.schemas.error import ErrorResponse
from data_service.schemas.inflation import InflationResponse
from data_service.security import require_api_key
from data_service.services.bcb_inflation_client import fetch_brl_inflation

router = APIRouter(
    prefix="/inflation",
    tags=["Inflation"],
    dependencies=[Depends(require_api_key)],
)


@router.get(
    "/brl",
    response_model=InflationResponse,
    responses={
        401: {"model": ErrorResponse, "description": "Missing or invalid API key"},
        502: {"model": ErrorResponse, "description": "Upstream data source unavailable"},
    },
)
def get_brl_inflation() -> InflationResponse:
    """Full monthly IPCA (Brazil's official inflation index) series, sourced from BCB's
    SGS series 433 via python-bcb. Each `rate` is the raw monthly rate as published
    (e.g. `0.5` meaning 0.5% that month), not an accumulated/compounded figure between
    two dates."""
    return fetch_brl_inflation()
