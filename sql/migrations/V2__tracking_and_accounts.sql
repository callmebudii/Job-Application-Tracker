ALTER TABLE candidate_applications ADD COLUMN IF NOT EXISTS tracking_token TEXT UNIQUE;
CREATE INDEX IF NOT EXISTS applications_job_id_idx ON candidate_applications(job_id);
CREATE INDEX IF NOT EXISTS applications_created_at_idx ON candidate_applications(created_at DESC);
CREATE TABLE IF NOT EXISTS admin_accounts (
 username TEXT PRIMARY KEY,
 password_hash TEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE
);
