class AssetNotFoundError(Exception):
    """Raised when yfinance has no data at all for the given ticker."""


class UpstreamFetchError(Exception):
    """Raised when the yfinance/Yahoo Finance call itself fails (network, rate limit, etc.),
    as opposed to the ticker simply not existing."""
