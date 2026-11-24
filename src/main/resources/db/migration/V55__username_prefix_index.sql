-- The default-collation index cannot serve a prefix search, so every player search on a prefix with few matches
-- scanned the whole players table. text_pattern_ops compares bytes, which serves the same equality lookups
-- and the prefix range in PlayerRepository.searchByUsernamePrefix.
DROP INDEX idx_players_username_upper;
CREATE INDEX idx_players_username_upper ON players (upper(username) text_pattern_ops);
