def build_cpi_csv(rows: list[dict], date_column: str = "observation_date") -> str:
    """Build fake FRED CPIAUCSL CSV text, matching the real endpoint's confirmed shape:
    a header row (`observation_date,CPIAUCSL`) followed by one row per month.

    Each row: {"date": "YYYY-MM-DD", "value": <float | None>}. A `None` value is
    rendered as `.`, FRED's own convention for a month with no published value yet
    (confirmed against the real October 2025 gap).
    """
    lines = [f"{date_column},CPIAUCSL"]
    for row in rows:
        value = "." if row["value"] is None else str(row["value"])
        lines.append(f"{row['date']},{value}")
    return "\n".join(lines) + "\n"


def empty_cpi_csv() -> str:
    return "observation_date,CPIAUCSL\n"


def malformed_cpi_csv() -> str:
    """A CSV missing both recognized date column names (`observation_date`/`date`) -
    e.g. a future FRED export format change."""
    return "unexpected_column,CPIAUCSL\n1947-01-01,21.48\n"
