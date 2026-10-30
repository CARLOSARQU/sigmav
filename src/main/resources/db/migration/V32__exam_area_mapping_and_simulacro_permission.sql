-- =============================================================================
-- V32: Refactor career mapping → area mapping + MANAGE_SIMULACROS permission
--
-- Rationale: Peruvian admission exams are per AREA (Ciencias, Letras, etc.),
-- not per career. Different careers may have different cut-off scores but
-- students take the same exam for a given university + area.
-- =============================================================================

-- 1. Replace exam_career_mapping with exam_area_mapping
DROP TABLE IF EXISTS exam_career_mapping;

CREATE TABLE IF NOT EXISTS exam_area_mapping (
    exam_id BIGINT NOT NULL
        REFERENCES exams(id) ON DELETE CASCADE,
    area_id BIGINT NOT NULL
        REFERENCES simulacro_areas(id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, area_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_area_mapping_exam ON exam_area_mapping(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_area_mapping_area ON exam_area_mapping(area_id);

-- 2. Add MANAGE_SIMULACROS permission (16th granular permission)
INSERT INTO permissions (name, description)
VALUES ('MANAGE_SIMULACROS', 'Manage simulacro configuration: universities, areas, and area-exam linking');

-- 3. Grant MANAGE_SIMULACROS to ACADEMY_ADMIN (already has all perms via SELECT, but new perm was added after that seed — catch it up)
INSERT INTO role_permissions (role, permission_name)
VALUES ('ACADEMY_ADMIN', 'MANAGE_SIMULACROS');

-- 4. Grant MANAGE_SIMULACROS to ACADEMIC_COORDINATOR (manages academic content including simulacros)
INSERT INTO role_permissions (role, permission_name)
VALUES ('ACADEMIC_COORDINATOR', 'MANAGE_SIMULACROS');
