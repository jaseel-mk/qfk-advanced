ALTER TABLE match_registrations ALTER COLUMN member_id DROP NOT NULL;
ALTER TABLE match_registrations ADD COLUMN guest_name varchar(160), ADD COLUMN guest_mobile varchar(20);
ALTER TABLE match_registrations ADD CONSTRAINT registration_player_identity CHECK ((member_id IS NOT NULL AND guest_name IS NULL AND guest_mobile IS NULL) OR (member_id IS NULL AND guest_name IS NOT NULL AND guest_mobile IS NOT NULL));
CREATE UNIQUE INDEX unique_guest_match_mobile ON match_registrations(match_id,guest_mobile) WHERE guest_mobile IS NOT NULL;
