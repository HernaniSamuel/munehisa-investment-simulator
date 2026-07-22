from datetime import date
from decimal import Decimal
from io import StringIO

import pandas as pd
import requests

from data_service.exceptions import UpstreamFetchError
from data_service.schemas.inflation import UsdInflationMonthlyDataPoint, UsdInflationResponse

_SERIES_ID = "CPIAUCSL"
_CSV_URL = f"https://fred.stlouisfed.org/graph/fredgraph.csv?id={_SERIES_ID}"
# No published SLA from FRED for this endpoint; chosen to fail in a reasonable time
# rather than hang, same reasoning as the BCB SGS client's explicit timeout.
_TIMEOUT_SECONDS = 10.0
# FRED has renamed this column before (confirmed by MineInvest defensively accepting
# both names) - checked in this order so a future rename doesn't surface as a raw,
# harder-to-diagnose KeyError.
_DATE_COLUMN_CANDIDATES = ("observation_date", "date")


def fetch_usd_inflation() -> UsdInflationResponse:
    """Fetch the US CPI-U's (Consumer Price Index for All Urban Consumers) full monthly
    history from FRED series CPIAUCSL, via its public CSV export endpoint (no API key
    required).

    No start/end is passed: the CSV's first row is confirmed empirically to start
    exactly at 1947-01-01, so it's safe to rely on FRED's default range rather than
    hardcoding a date here.

    The series has a genuine gap in the middle (e.g. October 2025, confirmed empirically
    - likely the Oct 2025 US government shutdown delaying BLS publication), represented
    in the CSV as a row with a `.` value. Parsed via `na_values=['.']` into a real NaN
    and dropped from the response entirely, rather than forward-filled - that would
    fabricate a CPI value FRED never published.

    There's no "not found" case here: CPIAUCSL is a single fixed series code, not a
    user-supplied parameter (like a ticker or currency pair) that could be unknown.

    Raises:
        UpstreamFetchError: the HTTP request failed (network, timeout, non-2xx status),
            the CSV couldn't be parsed, or it parsed but is missing an expected column.
    """
    try:
        response = requests.get(_CSV_URL, timeout=_TIMEOUT_SECONDS)
        response.raise_for_status()
        df = pd.read_csv(StringIO(response.text), na_values=["."])
        date_column = _find_date_column(df)
        df = df[[date_column, _SERIES_ID]]
    except Exception as exc:
        raise UpstreamFetchError(
            f"Failed to fetch CPI-U (FRED series {_SERIES_ID}): {exc}"
        ) from exc

    df = df.dropna(subset=[_SERIES_ID])

    if df.empty:
        raise UpstreamFetchError(f"FRED series {_SERIES_ID} returned no data")

    monthly_data = [
        _row_to_point(row[date_column], row[_SERIES_ID]) for _, row in df.iterrows()
    ]

    return UsdInflationResponse(
        start_date=monthly_data[0].date,
        monthly_data=monthly_data,
    )


def _find_date_column(df: pd.DataFrame) -> str:
    for name in _DATE_COLUMN_CANDIDATES:
        if name in df.columns:
            return name
    raise KeyError(
        f"Expected a {' or '.join(_DATE_COLUMN_CANDIDATES)} column, found: {list(df.columns)}"
    )


def _row_to_point(raw_date: str, value: float) -> UsdInflationMonthlyDataPoint:
    return UsdInflationMonthlyDataPoint(
        date=date.fromisoformat(raw_date), value=Decimal(str(value))
    )
