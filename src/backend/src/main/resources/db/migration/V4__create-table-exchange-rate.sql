CREATE TABLE exchange_rate
(
    id              UUID PRIMARY KEY,

    base_currency   VARCHAR(3) NOT NULL,
    quote_currency  VARCHAR(3) NOT NULL,
    reference_month DATE       NOT NULL,

    open            NUMERIC    NOT NULL,
    high            NUMERIC    NOT NULL,
    low             NUMERIC    NOT NULL,
    close           NUMERIC    NOT NULL,

    CONSTRAINT uk_exchange_rate_pair_reference_month UNIQUE (base_currency, quote_currency, reference_month),
    CONSTRAINT ck_exchange_rate_canonical_pair CHECK (base_currency < quote_currency)
);
