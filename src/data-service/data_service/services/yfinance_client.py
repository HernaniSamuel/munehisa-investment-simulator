from decimal import ROUND_HALF_UP, Decimal
from typing import Any

import pandas as pd
import yfinance as yf

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.schemas.asset import AssetResponse, MonthlyDataPoint

_CENTS = Decimal("0.01")
_OHLC_COLUMNS = ["Open", "High", "Low", "Close"]
_AGG_COLUMNS = {
    "Open": "first",
    "High": "max",
    "Low": "min",
    "Close": "last",
    "Volume": "sum",
    "Dividends": "sum",
    "Stock Splits": "sum",
}


def fetch_asset(ticker: str) -> AssetResponse:
    """Fetch a ticker's full history from yfinance and resample it to monthly OHLCV
    (+ dividends/splits), forward-filling gaps in price data.

    Raises:
        AssetNotFoundError: the ticker has no data at all (unknown/delisted/invalid symbol).
        UpstreamFetchError: the call to yfinance itself failed (network, rate limit, etc.).
    """
    ticker = ticker.upper()

    try:
        yf_ticker = yf.Ticker(ticker)
        info = yf_ticker.info
        hist = yf_ticker.history(period="max", auto_adjust=False, actions=True)
    except Exception as exc:
        raise UpstreamFetchError(f"Failed to fetch {ticker} from yfinance: {exc}") from exc

    if hist.empty:
        raise AssetNotFoundError(f"No data available for {ticker}")

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


def _row_to_point(dt: pd.Timestamp, row: "pd.Series[Any]") -> MonthlyDataPoint:
    dividends = row["Dividends"]
    splits = row["Stock Splits"]

    return MonthlyDataPoint(
        date=dt.date().replace(day=1),
        open=Decimal(str(row["Open"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        high=Decimal(str(row["High"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        low=Decimal(str(row["Low"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        close=Decimal(str(row["Close"])).quantize(_CENTS, rounding=ROUND_HALF_UP),
        volume=int(row["Volume"]) if pd.notna(row["Volume"]) else 0,
        dividends=Decimal(str(dividends)) if pd.notna(dividends) and dividends > 0 else None,
        splits=Decimal(str(splits)) if pd.notna(splits) and splits > 0 else None,
    )
