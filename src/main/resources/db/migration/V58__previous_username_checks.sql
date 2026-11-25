-- Released usernames are held by Mojang for 37 days, so a previous name can only belong to a new
-- account after that hold. Keyed on the lowercased name because Mojang names are case-insensitive.
CREATE TABLE previous_username_checks (
    username VARCHAR(16) PRIMARY KEY,
    due_at   TIMESTAMPTZ NOT NULL,
    attempts SMALLINT    NOT NULL DEFAULT 0
);

CREATE INDEX idx_previous_username_checks_due_at ON previous_username_checks (due_at);

INSERT INTO previous_username_checks (username, due_at)
SELECT lower(previous_username), max(timestamp) + INTERVAL '37 days'
FROM username_change_events
WHERE previous_username IS NOT NULL AND length(previous_username) <= 16
GROUP BY lower(previous_username);
