-- V26__update_role_check_constraint.sql
-- Drops the existing role CHECK constraint (name discovered dynamically) and
-- recreates it to accept the 3 new staff roles added in this change.
-- Existing rows remain valid because they only contain the 4 original values.

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'users'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%SUPER_ADMIN%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('SUPER_ADMIN', 'ACADEMY_ADMIN', 'TEACHER', 'STUDENT',
                    'SALES_ADVISOR', 'ACADEMIC_COORDINATOR', 'COLLECTIONS_MANAGER'));
