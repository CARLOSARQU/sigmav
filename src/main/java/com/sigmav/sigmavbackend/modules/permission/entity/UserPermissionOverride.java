package com.sigmav.sigmavbackend.modules.permission.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_permission_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermissionOverride {

    @EmbeddedId
    private UserPermissionOverrideId id;

    /**
     * Tenant-isolation field. Stored as a raw ID (not a FK entity reference)
     * to keep the query simple; the DB FK ensures referential integrity.
     */
    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    /** true = grant the permission; false = revoke it from role defaults. */
    @Column(nullable = false)
    private boolean granted;
}
