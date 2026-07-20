from datetime import date
from decimal import Decimal

from pydantic import BaseModel


class MonthlyDataPoint(BaseModel):
    """Single month of OHLCV data plus any dividends/splits that occurred that month."""

    date: date
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: int
    dividends: Decimal | None = None
    splits: Decimal | None = None


class AssetResponse(BaseModel):
    """Full historical series for a single ticker, resampled to monthly granularity."""

    ticker: str
    name: str
    base_currency: str
    start_date: date
    monthly_data: list[MonthlyDataPoint]
