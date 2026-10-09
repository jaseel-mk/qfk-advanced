ALTER TABLE match_lineup_settings DROP CONSTRAINT match_lineup_settings_starter_limit_check;
ALTER TABLE match_lineup_settings ADD CONSTRAINT match_lineup_settings_starter_limit_check CHECK(starter_limit BETWEEN 2 AND 11);
ALTER TABLE match_lineup_settings ADD COLUMN home_formation varchar(11), ADD COLUMN away_formation varchar(11);
