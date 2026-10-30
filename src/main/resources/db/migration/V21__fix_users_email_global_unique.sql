-- Reemplaza el unique compuesto (email, academy_id) por un unique global en email.
-- La unicidad global ya se aplica en la capa de aplicación; este constraint
-- la refuerza también a nivel de base de datos.

ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_email_academy;
DROP INDEX IF EXISTS idx_users_email;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
