-- Concurrent writers upserting the same skin rows in different orders deadlocked. Writers now only
-- append deltas, which never conflict, and a single job folds them into skin_current_owners.
CREATE TABLE skin_owner_deltas (
    skin_id BIGINT NOT NULL,
    delta   BIGINT NOT NULL
);

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO skin_owner_deltas (skin_id, delta)
    SELECT skin_id, COUNT(*) FROM new_rows GROUP BY skin_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO skin_owner_deltas (skin_id, delta)
    SELECT skin_id, -COUNT(*) FROM old_rows GROUP BY skin_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO skin_owner_deltas (skin_id, delta)
    SELECT skin_id, SUM(delta)
    FROM (
        SELECT o.skin_id, -1 AS delta
        FROM old_rows o JOIN new_rows n ON n.id = o.id
        WHERE o.skin_id IS DISTINCT FROM n.skin_id
        UNION ALL
        SELECT n.skin_id, 1
        FROM old_rows o JOIN new_rows n ON n.id = o.id
        WHERE o.skin_id IS DISTINCT FROM n.skin_id
    ) changes
    GROUP BY skin_id;
    RETURN NULL;
END;
$$;
