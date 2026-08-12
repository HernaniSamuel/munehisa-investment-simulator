from datetime import date
from decimal import Decimal

from pydantic import BaseModel, Field


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
    prices_split_adjusted: bool


class AssetSearchResult(BaseModel):
    """One match from a ticker/name search, in provider-agnostic terms.

    asset_type is aliased to assetType on the wire (issue #89's acceptance criteria
    specify that literal casing for this route), unlike every other snake_case field in
    this service - construct with the assetType= keyword, not asset_type=.
    """

    ticker: str
    name: str
    exchange: str | None = None
    asset_type: str | None = Field(default=None, alias="assetType")


class AssetSearchResponse(BaseModel):
    """Matches for a free-text ticker/name search, capped at 15 entries."""

    query: str
    results: list[AssetSearchResult]
