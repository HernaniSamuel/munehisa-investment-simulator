CREATE TABLE transactions
(
    id            UUID PRIMARY KEY,

    simulation_id UUID    NOT NULL REFERENCES simulation (id) ON DELETE CASCADE,
    type          VARCHAR NOT NULL,
    month         DATE    NOT NULL,
    amount        NUMERIC NOT NULL,

    ticker        VARCHAR,
    asset_name    VARCHAR,
    quantity      BIGINT,

    CONSTRAINT ck_transactions_type
        CHECK (type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAWAL', 'DIVIDEND')),
    CONSTRAINT ck_transactions_type_shape CHECK (
        (type IN ('BUY', 'SELL')
            AND ticker IS NOT NULL AND asset_name IS NOT NULL AND quantity IS NOT NULL)
        OR
        (type = 'DIVIDEND'
            AND ticker IS NOT NULL AND asset_name IS NOT NULL AND quantity IS NULL)
        OR
        (type IN ('DEPOSIT', 'WITHDRAWAL')
            AND ticker IS NULL AND asset_name IS NULL AND quantity IS NULL)
    )
);
