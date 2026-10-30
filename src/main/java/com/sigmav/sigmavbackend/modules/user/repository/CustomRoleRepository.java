package com.sigmav.sigmavbackend.modules.user.repository;

import com.sigmav.sigmavbackend.modules.user.entity.CustomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomRoleRepository extends JpaRepository<CustomRole, Long> {

    List<CustomRole> findByAcademyIdOrderByName(Long academyId);

    Optional<CustomRole> findByNameAndAcademyId(String name, Long academyId);

    boolean existsByNameAndAcademyId(String name, Long academyId);

    List<CustomRole> findByAcademyIdAndIsActiveTrue(Long academyId);
}
