-- Kept out of skins so a count change rewrites a tiny row with a single primary-key index instead of a
-- wide skins row with all of its indexes. The low fillfactor leaves room for same-page (HOT) updates.
CREATE TABLE skin_current_owners (
    skin_id BIGINT PRIMARY KEY,
    owners  BIGINT NOT NULL
) WITH (fillfactor = 70);

INSERT INTO skin_current_owners (skin_id, owners)
SELECT skin_id, COUNT(*) FROM players GROUP BY skin_id;

-- One upsert per distinct skin per statement, applied in id order so concurrent bulk writes
-- take row locks in the same sequence.
CREATE FUNCTION fn_players_skin_current_owners_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO skin_current_owners AS o (skin_id, owners)
    SELECT skin_id, COUNT(*) FROM new_rows GROUP BY skin_id ORDER BY skin_id
    ON CONFLICT (skin_id) DO UPDATE SET owners = o.owners + EXCLUDED.owners;
    RETURN NULL;
END;
$$;

CREATE FUNCTION fn_players_skin_current_owners_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE skin_current_owners o SET owners = o.owners - n.cnt
    FROM (SELECT skin_id, COUNT(*) AS cnt FROM old_rows GROUP BY skin_id ORDER BY skin_id) n
    WHERE o.skin_id = n.skin_id;
    RETURN NULL;
END;
$$;

CREATE FUNCTION fn_players_skin_current_owners_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO skin_current_owners AS o (skin_id, owners)
    SELECT skin_id, SUM(delta)
    FROM (
        SELECT o2.skin_id, -1 AS delta
        FROM old_rows o2 JOIN new_rows n ON n.id = o2.id
        WHERE o2.skin_id IS DISTINCT FROM n.skin_id
        UNION ALL
        SELECT n.skin_id, 1
        FROM old_rows o2 JOIN new_rows n ON n.id = o2.id
        WHERE o2.skin_id IS DISTINCT FROM n.skin_id
    ) changes
    GROUP BY skin_id
    ORDER BY skin_id
    ON CONFLICT (skin_id) DO UPDATE SET owners = o.owners + EXCLUDED.owners;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_players_skin_current_owners_insert
AFTER INSERT ON players
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_skin_current_owners_insert();

CREATE TRIGGER trg_players_skin_current_owners_delete
AFTER DELETE ON players
REFERENCING OLD TABLE AS old_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_skin_current_owners_delete();

CREATE TRIGGER trg_players_skin_current_owners_update
AFTER UPDATE ON players
REFERENCING OLD TABLE AS old_rows NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_players_skin_current_owners_update();
