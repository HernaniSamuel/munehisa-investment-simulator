from datetime import date
from decimal import Decimal
from typing import Any

import pandas as pd
import yfinance as yf

from data_service.exceptions import AssetNotFoundError, UpstreamFetchError
from data_service.schemas.exchange import ExchangeMonthlyDataPoint, ExchangeRateResponse

_OHLC_COLUMNS = ["Open", "High", "Low", "Close"]
_AGG_COLUMNS = {"Open": "first", "High": "max", "Low": "min", "Close": "last"}
# There's no real "history" for an identity rate (from_currency == to_currency, e.g.
# USD/USD) - this is a fixed, explicitly-synthetic sentinel date, not a claim about when
# the rate "started". Inventing one via MineInvest's arbitrary 2000-01-01 floor isn't
# this service's call to make.
_SAME_CURRENCY_DATE = date(1970, 1, 1)


def _build_symbol(from_currency: str, to_currency: str) -> str:
    return f"{from_currency}{to_currency}=X"


def _fetch_history(symbol: str) -> Any:
    # yfinance ships no type stubs, so `.history()` is Any all the way through - same as
    # fetch_asset's `hist`, not narrowed to DataFrame here on purpose (see resample/agg
    # usage below, which relies on this staying duck-typed rather than strictly checked).
    try:
        return yf.Ticker(symbol).history(period="max")
    except Exception as exc:
        raise UpstreamFetchError(f"Failed to fetch {symbol} from yfinance: {exc}") from exc


def fetch_exchange_rate(from_currency: str, to_currency: str) -> ExchangeRateResponse:
    """Fetch a currency pair's full monthly OHLC exchange-rate history from yfinance.

    Tries the direct symbol ({from}{to}=X) first; if it has no history, falls back to
    the inverse symbol ({to}{from}=X) and inverts the OHLC values. The response always
    reports the canonical direct-format symbol, regardless of which one was queried.

    Raises:
        AssetNotFoundError: neither the direct nor the inverse symbol has any data
            (unknown/invalid currency pair).
        UpstreamFetchError: the call to yfinance itself failed (network, rate limit, etc.).
    """
    from_currency = from_currency.upper()
    to_currency = to_currency.upper()
    direct_symbol = _build_symbol(from_currency, to_currency)

    if from_currency == to_currency:
        # yf.Ticker("USDUSD=X").history(period="max") raises outright (period='max' is
        # invalid for that symbol) rather than returning an empty history, so this must
        # be special-cased before ever reaching yfinance - not left to fall through and
        # be misread as a 404.
        return _same_currency_response(direct_symbol, from_currency, to_currency)

    inverse_symbol = _build_symbol(to_currency, from_currency)

    hist = _fetch_history(direct_symbol)
    inverted = False
    if hist.empty:
        hist = _fetch_history(inverse_symbol)
        inverted = True

    if hist.empty:
        # An invalid/unknown pair yields an empty history on both symbols (confirmed
        # empirically), as opposed to a raised exception - which is handled separately
        # above as an UpstreamFetchError, not conflated with "pair doesn't exist".
        raise AssetNotFoundError(
            f"No exchange rate data found for {from_currency}/{to_currency} "
            f"(tried {direct_symbol}, {inverse_symbol})"
        )

    hist_monthly = hist.resample("MS").agg(_AGG_COLUMNS)
    hist_monthly[_OHLC_COLUMNS] = hist_monthly[_OHLC_COLUMNS].ffill()

    # Reindex to a complete, gap-free monthly range (a no-op if resample already produced
    # one, but guards against edge cases where it doesn't) - same as fetch_asset.
    complete_range = pd.date_range(
        start=hist_monthly.index[0], end=hist_monthly.index[-1], freq="MS"
    )
    hist_monthly = hist_monthly.reindex(complete_range)
    hist_monthly[_OHLC_COLUMNS] = hist_monthly[_OHLC_COLUMNS].ffill()

    if inverted:
        hist_monthly = _invert(hist_monthly)

    monthly_data = [_row_to_point(dt, row) for dt, row in hist_monthly.iterrows()]

    return ExchangeRateResponse(
        symbol=direct_symbol,
        from_currency=from_currency,
        to_currency=to_currency,
        start_date=monthly_data[0].date,
        monthly_data=monthly_data,
    )


def _invert(hist_monthly: pd.DataFrame) -> pd.DataFrame:
    """Invert an OHLC frame fetched under the inverse symbol back into direct-pair terms.

    High and low swap, not just invert in place: inverting a ratio flips which side is
    larger, so the inverse series' low (its weakest point) becomes the direct series'
    high, and vice versa.
    """
    inverted = pd.DataFrame(index=hist_monthly.index)
    inverted["Open"] = 1.0 / hist_monthly["Open"]
    inverted["High"] = 1.0 / hist_monthly["Low"]
    inverted["Low"] = 1.0 / hist_monthly["High"]
    inverted["Close"] = 1.0 / hist_monthly["Close"]
    return inverted


def _row_to_point(dt: pd.Timestamp, row: pd.Series[Any]) -> ExchangeMonthlyDataPoint:
    # No quantization on purpose: real precision varies by pair (e.g. USDBRL close
    # 5.09119987487793 vs EURUSD close 1.1415525674819946) and doesn't follow one
    # universal convention the way stock OHLC does - forcing a fixed decimal count would
    # be an uninformed business-precision decision this service shouldn't make.
    # Decimal(str(v)) preserves whatever precision the fetch (and, for an inverted pair,
    # the 1/x division) actually produced.
    return ExchangeMonthlyDataPoint(
        date=dt.date().replace(day=1),
        open=Decimal(str(row["Open"])),
        high=Decimal(str(row["High"])),
        low=Decimal(str(row["Low"])),
        close=Decimal(str(row["Close"])),
    )


def _same_currency_response(
    symbol: str, from_currency: str, to_currency: str
) -> ExchangeRateResponse:
    one = Decimal(1)
    point = ExchangeMonthlyDataPoint(
        date=_SAME_CURRENCY_DATE, open=one, high=one, low=one, close=one
    )
    return ExchangeRateResponse(
        symbol=symbol,
        from_currency=from_currency,
        to_currency=to_currency,
        start_date=_SAME_CURRENCY_DATE,
        monthly_data=[point],
    )
