package com.sigmav.sigmavbackend.modules.permission.repository;

import com.sigmav.sigmavbackend.modules.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * Resolves effective permissions for a user in a specific academy using a
     * single UNION/EXCEPT native query:
     *   global built-in role defaults
     *   UNION the user's custom-role grants, scoped to the role's own academy
     *   UNION granted overrides for this user+academy
     *   EXCEPT revoked overrides for this user+academy
     *
     * The two grant kinds are separate branches because they live in separate
     * tables. Built-in grants are global by nature: {@code role_permissions} keys
     * them on the role name and has no owning academy to record. Custom grants are
     * tenant-owned and keyed on {@code custom_roles.id}, so the custom branch joins
     * on the (name, academy_id) pair — joining on the name alone would let one
     * academy's role resolve another academy's grants whenever the names collided.
     *
     * A deactivated custom role grants nothing: retiring a role must remove its
     * holders' access, and holders are deliberately allowed to outlive deactivation
     * while they wait for reassignment. Only the custom branch filters on
     * {@code is_active} — built-in grants have no {@code custom_roles} row and no
     * active/inactive concept, so a predicate reaching that branch would silently
     * erase them.
     *
     * UNION and EXCEPT are left-associative at equal precedence, so the result is
     * ((built-in ∪ custom ∪ granted) − revoked): a revocation always wins.
     *
     * Academy-scoped by design (NFR-3: cross-tenant isolation).
     */
    @Query(value = """
            SELECT rp.permission_name
            FROM role_permissions rp
            JOIN users u ON u.role = rp.role
            WHERE u.id = :userId
            UNION
            SELECT crp.permission_name
            FROM custom_role_permissions crp
            JOIN custom_roles cr ON cr.id = crp.custom_role_id
            JOIN users u ON u.role = cr.name AND u.academy_id = cr.academy_id
            WHERE u.id = :userId AND cr.is_active = true
            UNION
            SELECT permission_name
            FROM user_permission_overrides
            WHERE user_id = :userId AND academy_id = :academyId AND granted = true
            EXCEPT
            SELECT permission_name
            FROM user_permission_overrides
            WHERE user_id = :userId AND academy_id = :academyId AND granted = false
            """, nativeQuery = true)
    List<String> findEffectivePermissionsForUser(@Param("userId") Long userId,
                                                  @Param("academyId") Long academyId);

    /**
     * Returns all permission names sorted alphabetically.
     * Used by the admin UI to populate permission checkboxes when creating/editing roles.
     */
    @Query("SELECT p.name FROM Permission p ORDER BY p.name")
    List<String> findAllPermissionNames();

    /**
     * Returns the permission names granted to a custom role, addressed by its id.
     * The id is unique per academy, so this can never reach another tenant's rows.
     */
    @Query(value = "SELECT permission_name FROM custom_role_permissions WHERE custom_role_id = :customRoleId",
            nativeQuery = true)
    List<String> findPermissionsByCustomRoleId(@Param("customRoleId") Long customRoleId);

    /**
     * Deletes every permission grant of a custom role, addressed by its id.
     * Used to replace a custom role's permission set on update.
     */
    @Modifying
    @Query(value = "DELETE FROM custom_role_permissions WHERE custom_role_id = :customRoleId", nativeQuery = true)
    void deleteCustomRolePermissions(@Param("customRoleId") Long customRoleId);

    /**
     * Grants a permission to a custom role, addressed by its id.
     */
    @Modifying
    @Query(value = """
            INSERT INTO custom_role_permissions (custom_role_id, permission_name)
            VALUES (:customRoleId, :permissionName)
            """, nativeQuery = true)
    void insertCustomRolePermission(@Param("customRoleId") Long customRoleId,
                                    @Param("permissionName") String permissionName);
}
