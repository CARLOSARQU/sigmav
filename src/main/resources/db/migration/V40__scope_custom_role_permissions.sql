-- role_permissions mixes two kinds of grant: global built-in role defaults
-- (ACADEMY_ADMIN, TEACHER, ...) and tenant-owned custom-role grants. Nothing in
-- the schema distinguished them, and the PK (role, permission_name) made role
-- names a global namespace: a custom role named the same in two academies shared
-- one set of rows, so either academy could read and rewrite the other's grants.
--
-- This does not add a column to role_permissions. Built-in grants genuinely are
-- global and have no owning academy to record, and a PK column cannot be nullable
-- anyway. Instead custom grants move to a table keyed on custom_roles.id, which
-- is already unique per academy - so tenancy is derived, never duplicated.
--
-- The 37 built-in rows are not touched.

CREATE TABLE custom_role_permissions (
    custom_role_id  BIGINT          NOT NULL REFERENCES custom_roles(id) ON DELETE CASCADE,
    permission_name VARCHAR(100)    NOT NULL REFERENCES permissions(name) ON DELETE CASCADE,
    PRIMARY KEY (custom_role_id, permission_name)
);

-- Copy custom-role grants across. The join is by name, matching the resolution
-- the application used until now, so each academy's role keeps exactly the
-- permissions it effectively had. If two academies shared a name, this fans out
-- to one row per role, which is precisely the separation being introduced.
INSERT INTO custom_role_permissions (custom_role_id, permission_name)
SELECT cr.id, rp.permission_name
FROM role_permissions rp
JOIN custom_roles cr ON cr.name = rp.role;

-- Remove exactly those rows from the global table. Scoped by the join to
-- custom_roles, whose names can never be built-in: CustomRoleService rejects
-- Role.isBuiltIn names on create and on rename, and V36 seeded none.
DELETE FROM role_permissions rp
USING custom_roles cr
WHERE cr.name = rp.role;
