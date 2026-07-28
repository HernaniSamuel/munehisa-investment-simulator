CREATE TABLE inflation_index
(
    id                UUID PRIMARY KEY,

    currency          VARCHAR(3) NOT NULL,
    reference_month   DATE       NOT NULL,
    accumulated_index NUMERIC    NOT NULL,

    CONSTRAINT uk_inflation_index_currency_reference_month UNIQUE (currency, reference_month),
    CONSTRAINT ck_inflation_index_currency CHECK (currency IN ('BRL', 'USD'))
);
