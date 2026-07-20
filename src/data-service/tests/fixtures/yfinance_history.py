import pandas as pd

_COLUMNS = ["Open", "High", "Low", "Close", "Volume", "Dividends", "Stock Splits"]


def build_history_df(rows: list[dict]) -> pd.DataFrame:
    """Build a fake yf.Ticker().history()-shaped DataFrame from daily rows.

    Each row: {"date": "YYYY-MM-DD", "open", "high", "low", "close", "volume",
    "dividends" (optional, default 0.0), "splits" (optional, default 0.0)} - matching
    yfinance's own convention of 0.0 on days with no dividend/split event (splits are a
    ratio like 4.0 for a 4-for-1 split, not a multiplicative factor - a plain float, 0.0
    on non-split days, summed per month, same as dividends).
    """
    index = pd.DatetimeIndex([pd.Timestamp(r["date"]) for r in rows], name="Date")
    return pd.DataFrame(
        {
            "Open": [r["open"] for r in rows],
            "High": [r["high"] for r in rows],
            "Low": [r["low"] for r in rows],
            "Close": [r["close"] for r in rows],
            "Volume": [r["volume"] for r in rows],
            "Dividends": [r.get("dividends", 0.0) for r in rows],
            "Stock Splits": [r.get("splits", 0.0) for r in rows],
        },
        index=index,
    )


def empty_history_df() -> pd.DataFrame:
    return pd.DataFrame(columns=_COLUMNS)
