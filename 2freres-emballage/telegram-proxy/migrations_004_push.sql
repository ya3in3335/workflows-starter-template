-- Notifications push (Firebase Cloud Messaging) : un jeton par téléphone
CREATE TABLE IF NOT EXISTS push_tokens (
  token TEXT PRIMARY KEY,
  user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
  app TEXT NOT NULL DEFAULT 'client',        -- 'client' ou 'admin'
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS push_tokens_user ON push_tokens(user_id);
CREATE INDEX IF NOT EXISTS push_tokens_app ON push_tokens(app);
