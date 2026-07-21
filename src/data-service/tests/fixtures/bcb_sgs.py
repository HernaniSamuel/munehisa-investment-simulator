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


def empty_ipca_df() -> pd.DataFrame:
    return pd.DataFrame({_IPCA_SERIES_CODE: []}, index=pd.DatetimeIndex([], name="Date"))


def malformed_ipca_df() -> pd.DataFrame:
    """A frame missing the expected '433' column entirely - e.g. a future python-bcb
    version renaming it."""
    index = pd.DatetimeIndex([pd.Timestamp("1980-01-01")], name="Date")
    return pd.DataFrame({"unexpected_column": [6.62]}, index=index)
