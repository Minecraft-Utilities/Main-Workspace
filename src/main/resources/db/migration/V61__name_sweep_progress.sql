-- One cursor per swept name length: names of that length are enumerated by index, so only the
-- position is stored and names that turn out to be unclaimed cost no rows.
CREATE TABLE name_sweep_progress (
    length     SMALLINT PRIMARY KEY,
    next_index BIGINT   NOT NULL DEFAULT 0,
    passes     INTEGER  NOT NULL DEFAULT 0
);
