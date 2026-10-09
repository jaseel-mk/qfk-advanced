ALTER TABLE match_registrations ADD COLUMN preferred_position varchar(16) NOT NULL DEFAULT 'UTILITY'
 CHECK (preferred_position IN ('GOALKEEPER','DEFENDER','MIDFIELDER','FORWARD','UTILITY'));
