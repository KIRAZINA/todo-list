-- Performance indexes
CREATE INDEX idx_tasks_user_status_priority ON tasks (user_id, status, priority);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);
CREATE INDEX idx_users_role ON users (role);

-- Optimistic locking support
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
