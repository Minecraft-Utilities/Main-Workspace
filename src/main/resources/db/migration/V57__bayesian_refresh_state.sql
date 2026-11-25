-- Velocity is replaced by a gamma-Poisson evidence pair (decayed change count + observed days).
-- Old velocities have no meaningful conversion, so every player restarts from the prior; the hot
-- index predicate keeps its 0.5 threshold on the renamed column.
ALTER TABLE player_refresh_state RENAME COLUMN change_velocity TO change_count;
ALTER TABLE player_refresh_state ADD COLUMN observed_days DOUBLE PRECISION NOT NULL DEFAULT 0;
UPDATE player_refresh_state SET change_count = 0 WHERE change_count <> 0;
