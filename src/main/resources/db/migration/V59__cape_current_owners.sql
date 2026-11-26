ALTER TABLE capes ADD COLUMN current_owners BIGINT NOT NULL DEFAULT 0;

UPDATE capes c SET current_owners = n.cnt
FROM (SELECT cape_id, COUNT(*) AS cnt FROM players WHERE cape_id IS NOT NULL GROUP BY cape_id) n
WHERE c.id = n.cape_id;

-- One counter update per distinct cape per statement, applied in id order so concurrent bulk writes
-- take row locks in the same sequence.
CREATE FUNCTION fn_players_cape_current_owners_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE capes c SET current_owners = c.current_owners + n.cnt
    FROM (SELECT cape_id, COUNT(*) AS cnt FROM new_rows WHERE cape_id IS NOT NULL GROUP BY cape_id ORDER BY cape_id) n
    WHERE c.id = n.cape_id;
    RETURN NULL;
END;
$$;

CREATE FUNCTION fn_players_cape_current_owners_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE capes c SET current_owners = c.current_owners - n.cnt
    FROM (SELECT cape_id, COUNT(*) AS cnt FROM old_rows WHERE cape_id IS NOT NULL GROUP BY cape_id ORDER BY cape_id) n
    WHERE c.id = n.cape_id;
    RETURN NULL;
END;
$$;

CREATE FUNCTION fn_players_cape_current_owners_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE capes c SET current_owners = c.current_owners + d.delta
    FROM (
        SELECT cape_id, SUM(delta) AS delta
        FROM (
            SELECT o.cape_id, -1 AS delta
            FROM old_rows o JOIN new_rows n ON n.id = o.id
            WHERE o.cape_id IS NOT NULL AND o.cape_id IS DISTINCT FROM n.cape_id
            UNION ALL
            SELECT n.cape_id, 1
            FROM old_rows o JOIN new_rows n ON n.id = o.id
            WHERE n.cape_id IS NOT NULL AND o.cape_id IS DISTINCT FROM n.cape_id
        ) changes
        GROUP BY cape_id
        ORDER BY cape_id
    ) d
    WHERE c.id = d.cape_id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_players_cape_current_owners_insert
AFTER INSERT ON players
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_cape_current_owners_insert();

CREATE TRIGGER trg_players_cape_current_owners_delete
AFTER DELETE ON players
REFERENCING OLD TABLE AS old_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_cape_current_owners_delete();

CREATE TRIGGER trg_players_cape_current_owners_update
AFTER UPDATE ON players
REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_cape_current_owners_update();
