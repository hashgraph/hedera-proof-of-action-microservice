CREATE TABLE actions
(
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- original payload submitted with the action
    payload                    TEXT        NOT NULL,

    -- transaction ID of the message submit transaction
    transaction_id_num         BIGINT      NOT NULL,
    transaction_id_valid_start BIGINT      NOT NULL,

    UNIQUE (transaction_id_num, transaction_id_valid_start)
);

-- CREATE INDEX ON actions (payload);
CREATE INDEX ON actions (transaction_id_valid_start, transaction_id_num);

CREATE TABLE proofs
(
    action_id           BIGINT PRIMARY KEY REFERENCES actions (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consensus_timestamp BIGINT      NOT NULL,
    sequence_number     BIGINT      NOT NULL,
    running_hash        BYTEA       NOT NULL
);

CREATE INDEX ON proofs (action_id);
