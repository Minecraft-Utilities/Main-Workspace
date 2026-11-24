-- trending_heat moves off skins. Only a few thousand skins are ever trending, but the hourly rebuild
-- rewrote rows of a 20M-row table whose six indexes (one of them covering trending_heat) each took a
-- new entry per update. A side table keeps the rewrite proportional to the trending set.
CREATE TABLE skin_trending (
    skin_id BIGINT  NOT NULL PRIMARY KEY,
    heat    INTEGER NOT NULL
);

CREATE INDEX idx_skin_trending_heat ON skin_trending (heat DESC, skin_id ASC);

INSERT INTO skin_trending (skin_id, heat)
SELECT id, trending_heat FROM skins WHERE trending_heat > 0;

DROP INDEX idx_skins_unique_owners_id_covering;
DROP INDEX idx_skins_trending_heat_id;
ALTER TABLE skins DROP COLUMN trending_heat;

CREATE INDEX idx_skins_unique_owners_id_covering
    ON skins (unique_owners DESC, id) INCLUDE (texture_id, model, legacy, first_seen, first_seen_using_player_id);

ANALYZE skin_trending;
