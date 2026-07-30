CREATE TABLE snapshot
(
    id                UUID PRIMARY KEY,

    simulation_id     UUID    NOT NULL REFERENCES simulation (id) ON DELETE CASCADE,
    cash_balance      NUMERIC NOT NULL,
    total_asset_value NUMERIC NOT NULL,

    CONSTRAINT uk_snapshot_simulation_id UNIQUE (simulation_id)
);

CREATE TABLE snapshot_position
(
    id                       UUID PRIMARY KEY,

    snapshot_id              UUID    NOT NULL REFERENCES snapshot (id) ON DELETE CASCADE,
    ticker                   VARCHAR NOT NULL,
    asset_name               VARCHAR NOT NULL,
    quantity                 BIGINT  NOT NULL,
    weight                   NUMERIC NOT NULL,
    cost_basis               NUMERIC NOT NULL,
    total_dividends_received NUMERIC NOT NULL
);
