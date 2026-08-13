from fastapi import APIRouter

router = APIRouter(tags=["Health"])


@router.get("/health")
def get_health() -> dict[str, str]:
    """Unauthenticated liveness check for container/orchestrator health probes - unlike
    every other route here, this one deliberately carries no `X-API-Key` dependency."""
    return {"status": "ok"}
