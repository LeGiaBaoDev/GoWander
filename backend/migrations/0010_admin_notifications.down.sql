REVOKE UPDATE (notifications_seen_at) ON admin_credentials FROM gowander_admin;
ALTER TABLE admin_credentials DROP COLUMN notifications_seen_at;
