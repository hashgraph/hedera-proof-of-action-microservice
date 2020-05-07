ALTER TABLE actions ADD COLUMN client_id TEXT;

CREATE INDEX ON actions (client_id);

ALTER TABLE proofs ADD COLUMN transaction_hash BYTEA;
