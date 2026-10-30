-- 1. Create custom_roles table
CREATE TABLE custom_roles (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    name        VARCHAR(60)     NOT NULL,
    description TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE(name, academy_id)
);

CREATE INDEX idx_custom_roles_academy ON custom_roles(academy_id);

-- 2. Widen users.role column to accommodate custom role names
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50);

-- 3. Drop CHECK constraint on users.role to allow custom role names
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'users'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%role%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
