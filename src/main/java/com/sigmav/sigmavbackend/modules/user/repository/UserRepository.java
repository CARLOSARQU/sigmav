package com.sigmav.sigmavbackend.modules.user.repository;

import com.sigmav.sigmavbackend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndAcademyId(String email, Long academyId);

    Optional<User> findByIdAndAcademyId(Long id, Long academyId);

    Optional<User> findByUuid(UUID uuid);

    Optional<User> findByAcademyIdAndRole(Long academyId, String role);

    boolean existsByEmailAndAcademyId(String email, Long academyId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByDniAndAcademyIdAndRoleAndIdNot(String dni, Long academyId, String role, Long excludeId);

    long countByRole(String role);

    long countByRoleAndAcademyId(String role, Long academyId);

    List<User> findByRoleOrderByFullName(String role);

    List<User> findByRoleAndAcademyIdOrderByFullName(String role, Long academyId);

    List<User> findByAcademyIdAndIsActiveTrue(Long academyId);

    List<User> findByAcademyIdAndRoleAndIsActiveTrue(Long academyId, String role);
}
