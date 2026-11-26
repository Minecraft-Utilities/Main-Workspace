-- Character model of real usernames per length, trained once and then frozen: the sweep cursor
-- is a position in this model's ranking, so retraining would invalidate it. Deleting a model row
-- also drops its cursor, which restarts that length against a freshly trained model.
CREATE TABLE name_model (
    length     SMALLINT    PRIMARY KEY,
    model      BYTEA       NOT NULL,
    trained_on BIGINT      NOT NULL,
    trained_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE name_model_progress (
    length    SMALLINT PRIMARY KEY REFERENCES name_model (length) ON DELETE CASCADE,
    cost      INTEGER  NOT NULL,
    last_name TEXT,
    consumed  BIGINT   NOT NULL DEFAULT 0,
    passes    INTEGER  NOT NULL DEFAULT 0
);
