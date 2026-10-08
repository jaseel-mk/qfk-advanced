ALTER TABLE matches ADD COLUMN home_team_id uuid REFERENCES teams, ADD COLUMN away_team_id uuid REFERENCES teams,
 ADD COLUMN home_score integer CHECK(home_score>=0), ADD COLUMN away_score integer CHECK(away_score>=0);
ALTER TABLE matches ADD CONSTRAINT match_score_pair CHECK((home_score IS NULL)=(away_score IS NULL));
CREATE TABLE match_lineups(registration_id uuid PRIMARY KEY REFERENCES match_registrations, side varchar(4) NOT NULL CHECK(side IN ('HOME','AWAY')), position varchar(40) NOT NULL DEFAULT 'UTILITY', starter boolean NOT NULL DEFAULT true);
CREATE TABLE guest_match_statistics(registration_id uuid PRIMARY KEY REFERENCES match_registrations, goals integer NOT NULL CHECK(goals>=0), assists integer NOT NULL CHECK(assists>=0));
CREATE TABLE account_role_history(id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES user_accounts, previous_role varchar(30) NOT NULL, new_role varchar(30) NOT NULL, changed_by uuid NOT NULL REFERENCES user_accounts, changed_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE polls ADD COLUMN status varchar(10) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('DRAFT','OPEN')), ADD COLUMN opens_at timestamptz NOT NULL DEFAULT now(), ADD COLUMN eligible_team_id uuid REFERENCES teams;
UPDATE polls SET opens_at=least(created_at,closes_at-interval '1 second');
ALTER TABLE polls ADD CONSTRAINT poll_schedule CHECK(closes_at>opens_at);
ALTER TABLE announcements ADD COLUMN publish_at timestamptz, ADD COLUMN audience_team_id uuid REFERENCES teams;
CREATE TABLE announcement_reads(announcement_id uuid REFERENCES announcements, member_id uuid REFERENCES members, read_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(announcement_id,member_id));
ALTER TABLE tournament_teams ADD COLUMN group_name varchar(30) NOT NULL DEFAULT 'A';
ALTER TABLE tournament_fixtures ADD COLUMN stage varchar(10) NOT NULL DEFAULT 'LEAGUE' CHECK(stage IN ('LEAGUE','GROUP','KNOCKOUT')), ADD COLUMN round_number integer NOT NULL DEFAULT 1 CHECK(round_number>0), ADD COLUMN group_name varchar(30), ADD COLUMN winner_team_id uuid REFERENCES teams;
CREATE INDEX idx_lineup_side ON match_lineups(side);
CREATE INDEX idx_announcements_schedule ON announcements(publish_at,status);
