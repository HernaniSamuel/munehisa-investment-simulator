CREATE TABLE simulation
(
    id                UUID PRIMARY KEY,

    user_id           UUID       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR    NOT NULL,
    base_currency     VARCHAR(3) NOT NULL,
    start_month       DATE       NOT NULL,
    current_month     DATE       NOT NULL,
    cash_balance      NUMERIC    NOT NULL,
    total_asset_value NUMERIC    NOT NULL,

    CONSTRAINT ck_simulation_base_currency CHECK (base_currency IN ('BRL', 'USD'))
);

CREATE TABLE position
(
    id                       UUID PRIMARY KEY,

    simulation_id            UUID    NOT NULL REFERENCES simulation (id) ON DELETE CASCADE,
    asset_id                 UUID    NOT NULL REFERENCES asset_catalog (id),
    quantity                 BIGINT  NOT NULL,
    weight                   NUMERIC NOT NULL,
    cost_basis               NUMERIC NOT NULL,
    total_dividends_received NUMERIC NOT NULL,

    CONSTRAINT uk_position_simulation_asset UNIQUE (simulation_id, asset_id)
);
