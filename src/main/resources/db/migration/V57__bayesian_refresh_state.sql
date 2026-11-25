-- Velocity is replaced by a gamma-Poisson evidence pair (decayed change count + observed days).
-- Both statements are catalog-only: the rename keeps the hot/cold partial indexes, and a constant
-- default adds the column without rewriting the 60M+ row table. Old velocities stay as the initial
-- count and wash out through the decay on the next refreshes.
ALTER TABLE player_refresh_state RENAME COLUMN change_velocity TO change_count;
ALTER TABLE player_refresh_state ADD COLUMN observed_days DOUBLE PRECISION NOT NULL DEFAULT 0;
