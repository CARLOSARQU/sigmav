package com.sigmav.sigmavbackend.modules.permission.repository;

import com.sigmav.sigmavbackend.modules.permission.entity.UserPermissionOverride;
import com.sigmav.sigmavbackend.modules.permission.entity.UserPermissionOverrideId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UserPermissionOverrideId> {

    List<UserPermissionOverride> findByIdUserIdAndAcademyId(Long userId, Long academyId);
}
