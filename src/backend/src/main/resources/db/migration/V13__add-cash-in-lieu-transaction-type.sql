ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'CASH_IN_LIEU'));

ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_type_shape;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type_shape CHECK (
        (type IN ('BUY', 'SELL', 'CASH_IN_LIEU')
            AND ticker IS NOT NULL AND asset_name IS NOT NULL AND quantity IS NOT NULL)
        OR
        (type = 'DIVIDEND'
            AND ticker IS NOT NULL AND asset_name IS NOT NULL AND quantity IS NULL)
        OR
        (type IN ('DEPOSIT', 'WITHDRAWAL')
            AND ticker IS NULL AND asset_name IS NULL AND quantity IS NULL)
    );
