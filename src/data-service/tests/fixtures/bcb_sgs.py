import pandas as pd

_IPCA_SERIES_CODE = "433"


def build_ipca_df(rows: list[dict]) -> pd.DataFrame:
    """Build a fake bcb.sgs.get(433)-shaped DataFrame: a DatetimeIndex named 'Date' and
    a single float64 column literally named '433' (the series code as a string) - matching
    python-bcb's actual, confirmed-empirically shape.

    Each row: {"date": "YYYY-MM-DD", "rate": <float>}.
    """
    index = pd.DatetimeIndex([pd.Timestamp(r["date"]) for r in rows], name="Date")
    return pd.DataFrame({_IPCA_SERIES_CODE: [r["rate"] for r in rows]}, index=index)
