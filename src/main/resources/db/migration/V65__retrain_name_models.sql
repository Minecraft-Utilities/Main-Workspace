-- The model format changed (higher-order and positional contexts), so stored models cannot be read.
-- Deleting them also drops their sweep cursors, which are positions in the old rankings; each length
-- retrains and restarts on its next sweep.
DELETE FROM name_model;
