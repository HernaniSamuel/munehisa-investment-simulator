from datetime import date
from decimal import Decimal

from pydantic import BaseModel


class InflationMonthlyDataPoint(BaseModel):
    """Single month of IPCA data: the raw monthly rate as published (e.g. `0.5` meaning
    0.5% that month) - not an accumulated/compounded figure between two dates."""

    date: date
    rate: Decimal


class InflationResponse(BaseModel):
    """Full historical monthly IPCA (Brazil's official inflation index) series, sourced
    from BCB's SGS series 433."""

    start_date: date
    monthly_data: list[InflationMonthlyDataPoint]
