from pydantic import BaseModel


class ErrorResponse(BaseModel):
    """Structured error body, mirroring the Java backend's RestErrorMessage shape."""

    status: str
    message: str
