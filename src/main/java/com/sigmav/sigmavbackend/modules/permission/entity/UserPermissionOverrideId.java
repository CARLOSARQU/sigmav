package com.sigmav.sigmavbackend.modules.permission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserPermissionOverrideId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;
}
