-- Clean up corrupted Space Shooter scores for user Kyle (user_id = 1)
-- This removes all old buggy space shooter scores so we can start fresh

BEGIN TRANSACTION;

-- First, backup the current data
-- SELECT * FROM user_game_scores WHERE user_id = 1 AND game_name = 'space';

-- Delete all space shooter scores for Kyle (user_id = 1)
DELETE FROM user_game_scores WHERE user_id = 1 AND game_name = 'space';

-- Reset the user_game_stats for space shooter
DELETE FROM user_game_stats WHERE user_id = 1 AND game_name = 'space';

-- Now re-insert only the good scores (35000 and 25000 from the manual test)
-- These were the scores that worked correctly
INSERT INTO user_game_scores (user_id, game_name, score, level, timestamp) 
VALUES 
  (1, 'space', 25000, 1, DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)),
  (1, 'space', 35000, 1, CURRENT_TIMESTAMP);

-- The stats will be recalculated automatically when the query runs
-- Or we can manually update them here:
INSERT INTO user_game_stats (user_id, game_name, total_plays, best_score, total_score, average_score)
VALUES (1, 'space', 2, 35000, 60000, 30000.0);

COMMIT;
