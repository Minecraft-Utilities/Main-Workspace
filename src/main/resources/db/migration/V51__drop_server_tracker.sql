-- The tracker dataset has no writers left: its discovery/verification pipeline, refresh cycle
-- and read APIs are removed. tracker_player_history holds the foreign key into
-- tracker_servers, so it is dropped first.
DROP TABLE IF EXISTS tracker_player_history;
DROP TABLE IF EXISTS tracker_servers;
