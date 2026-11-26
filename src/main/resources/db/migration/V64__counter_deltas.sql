-- Every owner count is kept by appending deltas here, which never conflict, and one job folds them
-- into the counts. Updating the shared count rows in place let concurrent transactions lock them in
-- different orders and deadlock.
CREATE TYPE owner_counter AS ENUM ('skin_current', 'skin_unique', 'cape_current', 'cape_unique');

CREATE TABLE counter_deltas (
    counter owner_counter NOT NULL,
    id      BIGINT        NOT NULL,
    delta   BIGINT        NOT NULL
);

INSERT INTO counter_deltas (counter, id, delta)
SELECT 'skin_current', skin_id, delta FROM skin_owner_deltas;

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'skin_current', skin_id, COUNT(*) FROM new_rows GROUP BY skin_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'skin_current', skin_id, -COUNT(*) FROM old_rows GROUP BY skin_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_skin_current_owners_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'skin_current', skin_id, SUM(delta)
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

CREATE OR REPLACE FUNCTION fn_skin_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'skin_unique', skin_id, COUNT(*) FROM new_rows GROUP BY skin_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_cape_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'cape_unique', cape_id, COUNT(*) FROM new_rows GROUP BY cape_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_cape_current_owners_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'cape_current', cape_id, COUNT(*) FROM new_rows WHERE cape_id IS NOT NULL GROUP BY cape_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_cape_current_owners_delete()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'cape_current', cape_id, -COUNT(*) FROM old_rows WHERE cape_id IS NOT NULL GROUP BY cape_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_players_cape_current_owners_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO counter_deltas (counter, id, delta)
    SELECT 'cape_current', cape_id, SUM(delta)
    FROM (
        SELECT o.cape_id, -1 AS delta
        FROM old_rows o JOIN new_rows n ON n.id = o.id
        WHERE o.cape_id IS NOT NULL AND o.cape_id IS DISTINCT FROM n.cape_id
        UNION ALL
        SELECT n.cape_id, 1
        FROM old_rows o JOIN new_rows n ON n.id = o.id
        WHERE n.cape_id IS NOT NULL AND o.cape_id IS DISTINCT FROM n.cape_id
    ) changes
    GROUP BY cape_id;
    RETURN NULL;
END;
$$;

DROP TABLE skin_owner_deltas;
