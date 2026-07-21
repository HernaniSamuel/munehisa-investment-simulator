from datetime import date
from decimal import Decimal

from pydantic import BaseModel


class ExchangeMonthlyDataPoint(BaseModel):
    """Single month of OHLC exchange-rate data."""

    date: date
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal


class ExchangeRateResponse(BaseModel):
    """Full historical OHLC series for a currency pair, resampled to monthly granularity.

    `symbol` always reports the canonical direct-format Yahoo Finance symbol
    (`{from_currency}{to_currency}=X`), even when the data was actually fetched via the
    inverse symbol and inverted.
    """

    symbol: str
    from_currency: str
    to_currency: str
    start_date: date
    monthly_data: list[ExchangeMonthlyDataPoint]
