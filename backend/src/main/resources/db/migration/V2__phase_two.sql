ALTER TABLE matches ADD COLUMN venue varchar(180);
ALTER TABLE match_registrations ADD COLUMN payment_status varchar(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE match_registrations ADD COLUMN attendance_status varchar(20) NOT NULL DEFAULT 'NOT_MARKED';

CREATE TABLE user_accounts(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),member_id uuid NOT NULL UNIQUE REFERENCES members(id),email varchar(254) NOT NULL UNIQUE,password_hash varchar(100) NOT NULL,role varchar(30) NOT NULL DEFAULT 'MEMBER',enabled boolean NOT NULL DEFAULT true,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE refresh_tokens(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),user_id uuid NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,token_hash char(64) NOT NULL UNIQUE,expires_at timestamptz NOT NULL,revoked_at timestamptz,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id,expires_at);
CREATE INDEX idx_matches_starts_at ON matches(starts_at);
