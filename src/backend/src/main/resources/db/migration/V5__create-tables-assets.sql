CREATE TABLE asset_catalog
(
    id            UUID PRIMARY KEY,

    ticker        VARCHAR    NOT NULL,
    name          VARCHAR    NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    start_date    DATE       NOT NULL,

    CONSTRAINT uk_asset_catalog_ticker UNIQUE (ticker)
);

CREATE TABLE asset_monthly_price
(
    id              UUID PRIMARY KEY,

    ticker          VARCHAR NOT NULL REFERENCES asset_catalog (ticker) ON DELETE CASCADE,
    reference_month DATE    NOT NULL,

    open            NUMERIC NOT NULL,
    high            NUMERIC NOT NULL,
    low             NUMERIC NOT NULL,
    close           NUMERIC NOT NULL,
    volume          BIGINT  NOT NULL,
    dividends       NUMERIC,
    splits          NUMERIC,

    CONSTRAINT uk_asset_monthly_price_ticker_reference_month UNIQUE (ticker, reference_month)
);
