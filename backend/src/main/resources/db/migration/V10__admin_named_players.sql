ALTER TABLE match_registrations DROP CONSTRAINT registration_player_identity;
ALTER TABLE match_registrations ADD CONSTRAINT registration_player_identity CHECK (
 (member_id IS NOT NULL AND guest_name IS NULL AND guest_mobile IS NULL)
 OR (member_id IS NULL AND guest_name IS NOT NULL AND length(trim(guest_name)) > 0)
);
CREATE UNIQUE INDEX unique_named_guest_per_match ON match_registrations(match_id,lower(trim(guest_name)))
 WHERE member_id IS NULL AND guest_mobile IS NULL;
