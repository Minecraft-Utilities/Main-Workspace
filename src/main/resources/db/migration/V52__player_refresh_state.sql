-- Refresh scheduling state moves off the wide players row. Every background refresh rewrites
-- next_refresh_at / change_velocity / last_updated; on players that is a non-HOT update of a
-- ~200 byte tuple plus an insert into every players index (WAL-heavy at 60M+ rows). On this narrow
-- table the same update touches one small tuple and two index entries.
--
-- The hot/cold predicates MUST stay in sync with PlayerRefreshSchedule.HOT_VELOCITY_THRESHOLD and
-- the literals in PlayerRefreshRepository (kept literal so Postgres matches the partial indexes).
CREATE TABLE player_refresh_state (
    player_id       UUID             NOT NULL PRIMARY KEY,
    next_refresh_at TIMESTAMPTZ      NOT NULL,
    change_velocity DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_updated    TIMESTAMPTZ      NOT NULL,
    monthly_views   BIGINT           NOT NULL DEFAULT 0
);

INSERT INTO player_refresh_state (player_id, next_refresh_at, change_velocity, last_updated, monthly_views)
SELECT id, next_refresh_at, change_velocity, last_updated, monthly_views
FROM players;

CREATE INDEX idx_refresh_state_hot
    ON player_refresh_state (change_velocity DESC, monthly_views DESC, next_refresh_at ASC)
    WHERE change_velocity >= 0.5 OR monthly_views > 0;

CREATE INDEX idx_refresh_state_cold
    ON player_refresh_state (next_refresh_at ASC)
    WHERE change_velocity < 0.5 AND monthly_views = 0;

CREATE INDEX idx_refresh_state_viewed
    ON player_refresh_state (player_id)
    WHERE monthly_views > 0;

-- Dropping the columns also drops idx_players_next_refresh_at, idx_players_hot_refresh_priority
-- and the monthly_views partial index.
ALTER TABLE players
    DROP COLUMN next_refresh_at,
    DROP COLUMN change_velocity,
    DROP COLUMN last_updated,
    DROP COLUMN monthly_views;

ANALYZE player_refresh_state;
