-- One counter update per distinct skin/cape per statement instead of one per inserted row,
-- applied in id order so concurrent bulk inserts take row locks in the same sequence.
DROP TRIGGER trg_skin_adoption_unique_owners ON player_skin_adoptions;
DROP TRIGGER trg_cape_adoption_unique_owners ON player_cape_adoptions;
DROP FUNCTION fn_skin_adoption_unique_owners();
DROP FUNCTION fn_cape_adoption_unique_owners();

CREATE FUNCTION fn_skin_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE skins s SET unique_owners = s.unique_owners + n.cnt
    FROM (SELECT skin_id, count(*) AS cnt FROM new_rows GROUP BY skin_id ORDER BY skin_id) n
    WHERE s.id = n.skin_id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_skin_adoption_unique_owners
AFTER INSERT ON player_skin_adoptions
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_skin_adoption_unique_owners();

CREATE FUNCTION fn_cape_adoption_unique_owners()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE capes c SET unique_owners = c.unique_owners + n.cnt
    FROM (SELECT cape_id, count(*) AS cnt FROM new_rows GROUP BY cape_id ORDER BY cape_id) n
    WHERE c.id = n.cape_id;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_cape_adoption_unique_owners
AFTER INSERT ON player_cape_adoptions
REFERENCING NEW TABLE AS new_rows
FOR EACH STATEMENT EXECUTE FUNCTION fn_cape_adoption_unique_owners();
