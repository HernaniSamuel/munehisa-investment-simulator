from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.routes.assets import router as assets_router
from data_service.schemas.error import ErrorResponse

app = FastAPI(
    title="Munehisa Investment Simulator - Data Service",
    description="Fetches and normalizes third-party market data (no caching or business logic).",
    version="0.1.0",
)

app.include_router(assets_router)


@app.exception_handler(AssetNotFoundError)
def asset_not_found_handler(request: Request, exc: AssetNotFoundError) -> JSONResponse:
    return JSONResponse(
        status_code=404,
        content=ErrorResponse(status="NOT_FOUND", message=str(exc)).model_dump(),
    )


@app.exception_handler(UpstreamFetchError)
def upstream_fetch_error_handler(request: Request, exc: UpstreamFetchError) -> JSONResponse:
    return JSONResponse(
        status_code=502,
        content=ErrorResponse(status="BAD_GATEWAY", message=str(exc)).model_dump(),
    )


@app.exception_handler(HTTPException)
def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorResponse(
            status=_status_name(exc.status_code), message=str(exc.detail)
        ).model_dump(),
    )


_STATUS_NAMES = {401: "UNAUTHORIZED", 404: "NOT_FOUND", 502: "BAD_GATEWAY"}


def _status_name(status_code: int) -> str:
    return _STATUS_NAMES.get(status_code, str(status_code))
