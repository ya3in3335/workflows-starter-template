-- Connexion Firebase (Google / e-mail vérifié) : lien entre l'utilisateur Firebase et le compte local
ALTER TABLE users ADD COLUMN firebase_uid TEXT;
ALTER TABLE users ADD COLUMN email TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS users_firebase_uid ON users(firebase_uid);
