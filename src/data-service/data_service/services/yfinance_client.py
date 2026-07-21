from decimal import ROUND_HALF_UP, Decimal
from typing import Any

import pandas as pd
import yfinance as yf

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.schemas.asset import AssetResponse, MonthlyDataPoint

_CENTS = Decimal("0.01")
_OHLC_COLUMNS = ["Open", "High", "Low", "Close"]


def _decimal_sum(values: pd.Series[Any]) -> Decimal:
    """Sum a month's worth of daily values as Decimals, not as float64.

    pandas' built-in 'sum' aggregates in float64, which can introduce its own rounding
    error on top of whatever a single value already carries (e.g. the classic
    0.1 + 0.2 == 0.30000000000000004 - still there under pandas' display formatting,
    which rounds for print but doesn't change the underlying float). Converting each
    value to Decimal via its exact string repr *before* adding sidesteps binary-float
    addition entirely, so there's no artifact to clean up afterward.
    """
    return sum((Decimal(str(v)) for v in values), start=Decimal(0))


def _decimal_compound_ratio(values: pd.Series[Any]) -> Decimal:
    """Compound a month's worth of daily split ratios as Decimals.

    A split is a multiplicative factor, not an additive one: two splits in the same
    month (e.g. a 2-for-1 followed by a 3-for-1) compound to an effective 6-for-1
    (2 * 3), not 5 (2 + 3). yfinance fills no-split days with 0.0, so those are skipped
    rather than multiplied in - treating them as the identity value (1.0) would work
    too, but multiplying by zero would collapse the whole month to zero, the same bug
    this replaces (see the module's split-aggregation history for why 'prod' on the raw
    column was broken)."""
    ratio = Decimal(1)
    for v in values:
        decimal_v = Decimal(str(v))
        if decimal_v > 0:
            ratio *= decimal_v
    return ratio


_AGG_COLUMNS = {
    "Open": "first",
    "High": "max",
    "Low": "min",
    "Close": "last",
    "Volume": "sum",
    "Dividends": _decimal_sum,
    "Stock Splits": _decimal_compound_ratio,
}


def fetch_asset(ticker: str) -> AssetResponse:
    """Fetch a ticker's full history from yfinance and resample it to monthly OHLCV
    (+ dividends/splits), forward-filling gaps in price data.

    Raises:
        AssetNotFoundError: the ticker has no data at all (unknown/delisted/invalid symbol).
        UpstreamFetchError: the call to yfinance itself failed (network, rate limit, etc.).
    """
    ticker = ticker.upper()
    yf_ticker = yf.Ticker(ticker)

    try:
        hist = yf_ticker.history(period="max", auto_adjust=False, actions=True)
    except Exception as exc:
        raise UpstreamFetchError(f"Failed to fetch {ticker} from yfinance: {exc}") from exc

    if hist.empty:
        # A ticker that doesn't exist yields an empty history, so we can report 404
        # without ever touching `.info` - which is only meaningful, and only called,
        # once we already know the ticker is real.
        raise AssetNotFoundError(f"No data available for {ticker}")

    try:
        info = yf_ticker.info
    except Exception as exc:
        raise UpstreamFetchError(f"Failed to fetch {ticker} from yfinance: {exc}") from exc

    hist_monthly = hist.resample("MS").agg(_AGG_COLUMNS)
    hist_monthly[_OHLC_COLUMNS] = hist_monthly[_OHLC_COLUMNS].ffill()

    # Reindex to a complete, gap-free monthly range (a no-op if resample already produced
    # one, but guards against edge cases where it doesn't).
    complete_range = pd.date_range(
        start=hist_monthly.index[0], end=hist_monthly.index[-1], freq="MS"
    )
    hist_monthly = hist_monthly.reindex(complete_range)
    hist_monthly[_OHLC_COLUMNS] = hist_monthly[_OHLC_COLUMNS].ffill()
    # Volume, dividends and splits are intentionally NOT forward-filled: a month with no
    # trading data had no volume, no dividend, and no split - carrying over the previous
    # month's value would misrepresent what actually happened.

    monthly_data = [_row_to_point(dt, row) for dt, row in hist_monthly.iterrows()]

    name = info.get("longName") or info.get("shortName") or ticker
    currency = info.get("currency", "USD")

    return AssetResponse(
        ticker=ticker,
        name=name,
        base_currency=currency,
        start_date=hist_monthly.index[0].date(),
        monthly_data=monthly_data,
    )


def _row_to_point(dt: pd.Timestamp, row: pd.Series[Any]) -> MonthlyDataPoint:
    dividends = _to_decimal(row["Dividends"])
    splits = _to_decimal(row["Stock Splits"])

    return MonthlyDataPoint(
        date=dt.date().replace(day=1),
        open=Decimal(str(row["Open"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        high=Decimal(str(row["High"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        low=Decimal(str(row["Low"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        close=Decimal(str(row["Close"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        volume=int(row["Volume"]) if pd.notna(row["Volume"]) else 0,
        # 0 is _decimal_sum's identity (no dividend paid that month); 1 is
        # _decimal_compound_ratio's identity (no split that month) - each column's
        # "nothing happened" sentinel matches how it was aggregated, not a shared value.
        dividends=dividends if dividends is not None and dividends > 0 else None,
        splits=splits if splits is not None and splits != 1 else None,
    )


def _to_decimal(value: Decimal | float) -> Decimal | None:
    """`value` is already a Decimal for a month with real trading data (aggregated via
    `_decimal_sum`/`_decimal_compound_ratio`, both Decimal-based - no float-arithmetic
    artifact reaches here, see their docstrings), or NaN for a gap month introduced by
    the reindex in `fetch_asset`, in which case there's nothing to report. No
    quantization is applied here on purpose: the daily values are already exact, so
    rounding further would only throw away real source precision (some dividends carry
    6+ decimal places once split-adjusted) to enforce a business-precision ceiling that
    isn't this service's call to make - persistence/storage precision belongs to
    whatever consumes this API."""
    if isinstance(value, float):
        return None if pd.isna(value) else Decimal(str(value))
    return value
