import logging
import sys
import time
import uuid
from contextvars import ContextVar

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pythonjsonlogger.json import JsonFormatter
from starlette.middleware.base import RequestResponseEndpoint

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.routes.assets import router as assets_router
from data_service.routes.exchange import router as exchange_router
from data_service.routes.health import router as health_router
from data_service.routes.inflation import router as inflation_router
from data_service.schemas.error import ErrorResponse

_request_id_ctx: ContextVar[str | None] = ContextVar("request_id", default=None)


class _RequestIdLogFilter(logging.Filter):
    """Injects the current request's id (if any) onto every LogRecord, so a log line
    emitted while handling a request can be correlated with that request's access-log
    line - see request_logging_middleware below."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.requestId = _request_id_ctx.get()
        return True


_handler = logging.StreamHandler(sys.stdout)
_handler.setFormatter(
    JsonFormatter(
        "{levelname}{name}{message}",
        style="{",
        rename_fields={"levelname": "level", "name": "logger"},
        timestamp=True,
    )
)
_handler.addFilter(_RequestIdLogFilter())

# force=True replaces any handler already attached to the root logger (e.g. uvicorn's own
# plain-text one) with the JSON handler above - a plain no-op basicConfig call would leave
# uvicorn's handler (and its plain-text format) in place. uvicorn's own access logger is
# disabled below so it doesn't also print a second, differently-formatted access line for
# every request alongside request_logging_middleware's line.
logging.basicConfig(level=logging.INFO, handlers=[_handler], force=True)
logging.getLogger("uvicorn.access").disabled = True

logger = logging.getLogger(__name__)

app = FastAPI(
    title="Munehisa Investment Simulator - Data Service",
    description=(
        "Fetches and normalizes third-party market data "
        "(no persistence or business logic; a short in-memory search cache aside)."
    ),
    version="0.1.0",
)

app.include_router(assets_router)
app.include_router(exchange_router)
app.include_router(inflation_router)
app.include_router(health_router)


@app.middleware("http")
async def request_logging_middleware(
    request: Request, call_next: RequestResponseEndpoint
) -> Response:
    # unhandled_exception_handler (registered for the bare Exception type) runs in
    # Starlette's ServerErrorMiddleware, which wraps *outside* this middleware - so an
    # exception that reaches it propagates straight out of call_next below, past this
    # function entirely. requestId is deliberately never reset here: resetting it before
    # re-raising would clear it before that handler gets a chance to log, breaking the
    # "access line and error line share a requestId" guarantee for the 500 case.
    request_id = str(uuid.uuid4())
    _request_id_ctx.set(request_id)
    start = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:
        duration_ms = round((time.perf_counter() - start) * 1000)
        logger.info(
            "request completed",
            extra={
                "method": request.method,
                "path": request.url.path,
                "status": 500,
                "durationMs": duration_ms,
            },
        )
        raise
    duration_ms = round((time.perf_counter() - start) * 1000)
    logger.info(
        "request completed",
        extra={
            "method": request.method,
            "path": request.url.path,
            "status": response.status_code,
            "durationMs": duration_ms,
        },
    )
    return response


@app.exception_handler(AssetNotFoundError)
def asset_not_found_handler(request: Request, exc: AssetNotFoundError) -> JSONResponse:
    logger.warning("%s: %s", type(exc).__name__, str(exc))
    return JSONResponse(
        status_code=404,
        content=ErrorResponse(status="NOT_FOUND", message=str(exc)).model_dump(),
    )


@app.exception_handler(UpstreamFetchError)
def upstream_fetch_error_handler(request: Request, exc: UpstreamFetchError) -> JSONResponse:
    logger.error("%s: %s", type(exc).__name__, str(exc))
    return JSONResponse(
        status_code=502,
        content=ErrorResponse(status="BAD_GATEWAY", message=str(exc)).model_dump(),
    )


@app.exception_handler(RequestValidationError)
def request_validation_error_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    # FastAPI's built-in handler would answer {"detail": [...]}, the only response in this
    # service not shaped {status, message}. /assets/search is the first route with
    # constrained params, so this is the first request that can reach a 422 at all.
    errors = exc.errors()
    message = errors[0]["msg"] if errors else "Invalid request parameters."
    logger.warning("validation error: %s", message)
    return JSONResponse(
        status_code=422,
        content=ErrorResponse(status="UNPROCESSABLE_ENTITY", message=message).model_dump(),
    )


@app.exception_handler(HTTPException)
def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    logger.warning("%s: %s", type(exc).__name__, exc.detail)
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


_STATUS_NAMES = {
    401: "UNAUTHORIZED",
    404: "NOT_FOUND",
    422: "UNPROCESSABLE_ENTITY",
    502: "BAD_GATEWAY",
}


def _status_name(status_code: int) -> str:
    return _STATUS_NAMES.get(status_code, str(status_code))


if __name__ == "__main__":
    import uvicorn

    from data_service.config import settings

    # Loopback-only by default: outside a container, this service has no network-level
    # isolation, and the API key is the only thing standing between it and any request
    # that reaches it. DATA_SERVICE_HOST lets the containerized deployment opt into
    # binding 0.0.0.0 without changing this default.
    #
    # log_config=None stops uvicorn from installing its own plain-text handlers on the
    # uvicorn/uvicorn.error loggers, so its startup/shutdown messages flow through the
    # JSON handler configured above instead of printing as plain text.
    uvicorn.run(app, host=settings.host, port=settings.port, log_config=None)
