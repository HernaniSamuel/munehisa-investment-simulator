from decimal import Decimal

import pandas as pd
from bcb import sgs

from data_service.exceptions import UpstreamFetchError
from data_service.schemas.inflation import InflationMonthlyDataPoint, InflationResponse

_IPCA_SERIES_CODE = 433
# python-bcb's own default is no timeout at all. Confirmed empirically: a request
# against a bad series code doesn't fail fast - it took several minutes before
# python-bcb gave up on its own and raised SGSError. An explicit, bounded timeout is
# required so a broken/unreachable BCB API fails in a reasonable time instead of
# hanging for minutes.
_TIMEOUT_SECONDS = 10.0


def fetch_brl_inflation() -> InflationResponse:
    """Fetch Brazil's full monthly IPCA (official inflation index) history from BCB's
    SGS series 433, via python-bcb.

    No start/end is passed: sgs.get(433) with no range returns the complete history,
    confirmed empirically to start exactly at 1980-01-01 - so it's safe to rely on the
    library's default range rather than hardcoding a date here.

    There's no "not found" case here: 433 is a single fixed series code, not a
    user-supplied parameter (like a ticker or currency pair) that could be unknown.

    Raises:
        UpstreamFetchError: the call to BCB's SGS API failed for any reason (network,
            timeout, bad response, etc.), or it succeeded but returned no usable data
            (empty frame, or the expected column missing/renamed).
    """
    try:
        df = sgs.get(_IPCA_SERIES_CODE, timeout=_TIMEOUT_SECONDS)
        # The returned column is literally named '433' (the series code as a string) -
        # renamed here to a proper field name rather than leaking BCB's raw column name
        # into the response schema. Looked up inside the try, alongside the call itself:
        # a missing/renamed column is just as much an "upstream didn't give us what we
        # expected" failure as sgs.get() raising outright, same as hist.empty is checked
        # in yfinance_client/yfinance_exchange_client before ever touching row 0.
        series = df[str(_IPCA_SERIES_CODE)]
    except Exception as exc:
        raise UpstreamFetchError(
            f"Failed to fetch IPCA (BCB SGS series {_IPCA_SERIES_CODE}): {exc}"
        ) from exc

    if series.empty:
        raise UpstreamFetchError(f"BCB SGS series {_IPCA_SERIES_CODE} returned no data")

    # No aggregation happens on our side (unlike the asset endpoint's dividends), so a
    # plain Decimal(str(v)) per value is sufficient - no float-artifact cleanup needed.
    monthly_data = [_row_to_point(dt, value) for dt, value in series.items()]

    return InflationResponse(
        start_date=monthly_data[0].date,
        monthly_data=monthly_data,
    )


def _row_to_point(dt: pd.Timestamp, value: float) -> InflationMonthlyDataPoint:
    return InflationMonthlyDataPoint(date=dt.date(), rate=Decimal(str(value)))
