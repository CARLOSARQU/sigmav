-- V30__add_simulacro_scope.sql
-- Adds 'SIMULACRO' to the exams.scope_type CHECK constraint.
-- Follows the same dynamic-constraint-discovery pattern as V26.
-- Preserves all existing data.

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'exams'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%scope_type%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE exams DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

ALTER TABLE exams ADD CONSTRAINT chk_exam_scope
    CHECK (scope_type IN ('CYCLE', 'AREA', 'COURSE', 'SIMULACRO'));
