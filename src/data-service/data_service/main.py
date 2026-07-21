import logging

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.routes.assets import router as assets_router
from data_service.routes.exchange import router as exchange_router
from data_service.schemas.error import ErrorResponse

# A no-op if the root logger already has a handler (e.g. uvicorn configured its own
# before this module was imported) - otherwise this is the only thing standing between
# our logs and Python's "handler of last resort" (INFO dropped entirely, WARNING+ shown
# with no timestamp/level/logger name). Every future service in this repo will import
# `logging.getLogger(__name__)` the same way this one does, so it's worth getting a
# real baseline config in place now rather than each one rediscovering this gap.
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s"
)

logger = logging.getLogger(__name__)

app = FastAPI(
    title="Munehisa Investment Simulator - Data Service",
    description="Fetches and normalizes third-party market data (no caching or business logic).",
    version="0.1.0",
)

app.include_router(assets_router)
app.include_router(exchange_router)


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


@app.exception_handler(Exception)
def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    # Catch-all so every error response - including bugs we didn't anticipate - keeps the
    # same {status, message} shape instead of falling through to FastAPI's default
    # {"detail": ...} 500 body. The real exception is logged, not echoed to the client.
    logger.exception("Unhandled exception while processing %s %s", request.method, request.url)
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            status="INTERNAL_SERVER_ERROR", message="Internal server error."
        ).model_dump(),
    )


_STATUS_NAMES = {401: "UNAUTHORIZED", 404: "NOT_FOUND", 502: "BAD_GATEWAY"}


def _status_name(status_code: int) -> str:
    return _STATUS_NAMES.get(status_code, str(status_code))


if __name__ == "__main__":
    import uvicorn

    from data_service.config import settings

    # Loopback-only: this service has no network-level isolation yet (deferred to a
    # future hosting decision per the issue), and the API key is the only thing standing
    # between it and any request that reaches it. Binding wider than 127.0.0.1 is a
    # deliberate, separate decision to make once that hosting story exists.
    uvicorn.run(app, host="127.0.0.1", port=settings.port)
