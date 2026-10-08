CREATE UNIQUE INDEX idx_active_team_member ON team_members(team_id,member_id) WHERE left_at IS NULL;
CREATE TABLE tournaments (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name varchar(180) NOT NULL,
 starts_on date NOT NULL, ends_on date NOT NULL, venue varchar(180) NOT NULL,
 status varchar(30) NOT NULL CHECK(status IN ('DRAFT','OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
 description text NOT NULL DEFAULT '', CHECK(ends_on >= starts_on)
);
CREATE TABLE tournament_teams (
 tournament_id uuid REFERENCES tournaments NOT NULL, team_id uuid REFERENCES teams NOT NULL,
 PRIMARY KEY(tournament_id,team_id)
);
CREATE TABLE tournament_fixtures (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tournament_id uuid REFERENCES tournaments NOT NULL,
 home_team_id uuid NOT NULL, away_team_id uuid NOT NULL, starts_at timestamptz NOT NULL,
 home_score integer CHECK(home_score>=0), away_score integer CHECK(away_score>=0),
 FOREIGN KEY(tournament_id,home_team_id) REFERENCES tournament_teams,
 FOREIGN KEY(tournament_id,away_team_id) REFERENCES tournament_teams,
 CHECK(home_team_id <> away_team_id), CHECK((home_score IS NULL) = (away_score IS NULL))
);
CREATE TABLE finance_entries (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), kind varchar(10) NOT NULL CHECK(kind IN ('INCOME','EXPENSE')),
 amount numeric(12,2) NOT NULL CHECK(amount>0), currency char(3) NOT NULL DEFAULT 'QAR',
 category varchar(80) NOT NULL, description varchar(500) NOT NULL, occurred_on date NOT NULL,
 member_id uuid REFERENCES members, match_id uuid REFERENCES matches,
 created_by uuid REFERENCES user_accounts NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 voided_at timestamptz, voided_by uuid REFERENCES user_accounts, void_reason varchar(500)
);
CREATE TABLE player_match_statistics (
 match_id uuid REFERENCES matches NOT NULL, member_id uuid REFERENCES members NOT NULL,
 goals integer NOT NULL DEFAULT 0 CHECK(goals>=0), assists integer NOT NULL DEFAULT 0 CHECK(assists>=0),
 updated_by uuid REFERENCES user_accounts NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(match_id,member_id), FOREIGN KEY(match_id,member_id) REFERENCES match_registrations(match_id,member_id)
);
CREATE TABLE polls (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), question varchar(300) NOT NULL, closes_at timestamptz NOT NULL,
 closed boolean NOT NULL DEFAULT false, created_by uuid REFERENCES user_accounts NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE poll_options (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), poll_id uuid REFERENCES polls NOT NULL,
 label varchar(160) NOT NULL, UNIQUE(poll_id,label), UNIQUE(poll_id,id)
);
CREATE TABLE poll_votes (
 poll_id uuid REFERENCES polls NOT NULL, member_id uuid REFERENCES members NOT NULL, option_id uuid NOT NULL,
 voted_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(poll_id,member_id),
 FOREIGN KEY(poll_id,option_id) REFERENCES poll_options(poll_id,id)
);
CREATE TABLE announcements (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), title varchar(180) NOT NULL, body text NOT NULL,
 priority varchar(20) NOT NULL CHECK(priority IN ('NORMAL','IMPORTANT')),
 status varchar(20) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 created_by uuid REFERENCES user_accounts NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
