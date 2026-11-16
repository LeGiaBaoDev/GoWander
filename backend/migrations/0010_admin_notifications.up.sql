-- The Admin's bell (owner, 16-11-2026): notifications come from the audit log; this keeps when each operator last
-- opened them, so the bell can count what is new.
ALTER TABLE admin_credentials ADD COLUMN notifications_seen_at TIMESTAMPTZ;
GRANT UPDATE (notifications_seen_at) ON admin_credentials TO gowander_admin;
