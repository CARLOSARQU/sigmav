-- V27__add_permissions_tables.sql
-- Creates permissions infrastructure: catalogue table, role-permission defaults,
-- and per-user academy-scoped override table. Seeds 15 permissions and the
-- default role-permission matrix.

CREATE TABLE permissions (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(100)    UNIQUE NOT NULL,
    description TEXT            NOT NULL
);

CREATE TABLE role_permissions (
    role            VARCHAR(60)     NOT NULL,
    permission_name VARCHAR(100)    NOT NULL REFERENCES permissions(name) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_name)
);

CREATE TABLE user_permission_overrides (
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_name VARCHAR(100)    NOT NULL REFERENCES permissions(name) ON DELETE CASCADE,
    academy_id      BIGINT          NOT NULL REFERENCES academies(id) ON DELETE CASCADE,
    granted         BOOLEAN         NOT NULL,
    PRIMARY KEY (user_id, permission_name)
);

CREATE INDEX idx_user_permission_overrides_academy ON user_permission_overrides(academy_id);

-- ============================================================
-- Seed: 15 permissions catalogue
-- ============================================================
INSERT INTO permissions (name, description) VALUES
    ('REGISTER_STUDENT',      'Create student accounts and enroll them'),
    ('VIEW_ENROLLMENTS',      'Read enrollment list and status'),
    ('MANAGE_ENROLLMENTS',    'Update or cancel enrollments and set payment status'),
    ('READ_FINANCES',         'View payment reports, cash reports, delinquency alerts'),
    ('WRITE_FINANCES',        'Record manual payments'),
    ('MANAGE_SCHEDULE',       'Create, edit and delete sessions and schedules'),
    ('ASSIGN_TEACHERS',       'Assign teachers to cycles and courses'),
    ('MANAGE_EXAMS',          'Create, edit and delete exams and simulacros'),
    ('VIEW_ATTENDANCE',       'Read attendance records'),
    ('MANAGE_ATTENDANCE',     'Mark and edit attendance'),
    ('EDIT_GRADES',           'Modify student grades'),
    ('VIEW_REPORTS',          'Access academic reports (non-financial)'),
    ('MANAGE_STUDENTS',       'Edit student profiles and reset passwords'),
    ('MANAGE_ACADEMY_CONFIG', 'Academy settings and feature flags (admin only)'),
    ('MANAGE_ROLES',          'Assign roles to users (admin only)');

-- ============================================================
-- Seed: role-permission default matrix
-- SUPER_ADMIN  → 0 rows (exempt; bypassed in service layer)
-- STUDENT      → 0 rows (access enforced at service layer)
-- ============================================================

-- ACADEMY_ADMIN: all 15 permissions
INSERT INTO role_permissions (role, permission_name)
SELECT 'ACADEMY_ADMIN', name FROM permissions;

-- SALES_ADVISOR
INSERT INTO role_permissions (role, permission_name) VALUES
    ('SALES_ADVISOR', 'REGISTER_STUDENT'),
    ('SALES_ADVISOR', 'VIEW_ENROLLMENTS'),
    ('SALES_ADVISOR', 'MANAGE_ENROLLMENTS'),
    ('SALES_ADVISOR', 'MANAGE_STUDENTS');

-- ACADEMIC_COORDINATOR
INSERT INTO role_permissions (role, permission_name) VALUES
    ('ACADEMIC_COORDINATOR', 'MANAGE_SCHEDULE'),
    ('ACADEMIC_COORDINATOR', 'ASSIGN_TEACHERS'),
    ('ACADEMIC_COORDINATOR', 'MANAGE_EXAMS'),
    ('ACADEMIC_COORDINATOR', 'VIEW_ATTENDANCE'),
    ('ACADEMIC_COORDINATOR', 'MANAGE_ATTENDANCE'),
    ('ACADEMIC_COORDINATOR', 'EDIT_GRADES'),
    ('ACADEMIC_COORDINATOR', 'VIEW_REPORTS'),
    ('ACADEMIC_COORDINATOR', 'MANAGE_STUDENTS');

-- COLLECTIONS_MANAGER
INSERT INTO role_permissions (role, permission_name) VALUES
    ('COLLECTIONS_MANAGER', 'VIEW_ENROLLMENTS'),
    ('COLLECTIONS_MANAGER', 'READ_FINANCES'),
    ('COLLECTIONS_MANAGER', 'WRITE_FINANCES');

-- TEACHER
INSERT INTO role_permissions (role, permission_name) VALUES
    ('TEACHER', 'MANAGE_EXAMS'),
    ('TEACHER', 'VIEW_ATTENDANCE'),
    ('TEACHER', 'MANAGE_ATTENDANCE'),
    ('TEACHER', 'EDIT_GRADES'),
    ('TEACHER', 'VIEW_REPORTS');
