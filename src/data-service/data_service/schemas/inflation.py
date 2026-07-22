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


class UsdInflationMonthlyDataPoint(BaseModel):
    """Single month of CPI-U data: the raw index level as published (e.g. `332.568`) -
    not a computed inflation rate. Unlike IPCA, CPI-U is an index, not a percentage."""

    date: date
    value: Decimal


class UsdInflationResponse(BaseModel):
    """Full historical monthly CPI-U (US Consumer Price Index for All Urban Consumers)
    series, sourced from FRED series CPIAUCSL. A month with no published value (e.g. a
    publication delay) is simply absent from `monthly_data` rather than interpolated."""

    start_date: date
    monthly_data: list[UsdInflationMonthlyDataPoint]
