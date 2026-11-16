-- Feedbacks: guest-provided identity is unverified. Private screenshots must never use public place-photo URLs.
CREATE TABLE feedback_reports (
  id UUID PRIMARY KEY,
  kind VARCHAR(20) NOT NULL CHECK (kind IN ('INFORMATION','BUG','SUGGESTION','OTHER')),
  message TEXT NOT NULL CHECK (length(btrim(message)) BETWEEN 1 AND 1000),
  context VARCHAR(200) NOT NULL DEFAULT '',
  sender_name VARCHAR(100) NOT NULL DEFAULT '',
  sender_email VARCHAR(254) NOT NULL DEFAULT '',
  image BYTEA CHECK (octet_length(image) <= 2097152),
  status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','IN_REVIEW','RESOLVED','REJECTED')),
  note TEXT NOT NULL DEFAULT '' CHECK (length(note) <= 2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX feedback_reports_created_idx ON feedback_reports (created_at DESC, id);
CREATE INDEX feedback_reports_status_idx ON feedback_reports (status, created_at DESC);
CREATE TABLE feedback_events (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  report_id UUID NOT NULL REFERENCES feedback_reports(id) ON DELETE CASCADE,
  actor_id UUID NOT NULL,
  from_status VARCHAR(20) NOT NULL CHECK (from_status IN ('NEW','IN_REVIEW','RESOLVED','REJECTED')),
  to_status VARCHAR(20) NOT NULL CHECK (to_status IN ('NEW','IN_REVIEW','RESOLVED','REJECTED')),
  note TEXT NOT NULL DEFAULT '' CHECK (length(note) <= 2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX feedback_events_report_idx ON feedback_events (report_id, created_at);
-- Public API can submit only; no public read of messages, contacts or image data.
GRANT INSERT (id,kind,message,context,sender_name,sender_email,image) ON feedback_reports TO gowander_app;
GRANT SELECT ON feedback_reports,feedback_events TO gowander_admin;
GRANT UPDATE (status,note,updated_at) ON feedback_reports TO gowander_admin;
GRANT INSERT ON feedback_events TO gowander_admin;
