package com.sigmav.sigmavbackend.modules.academy.repository;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcademyRepository extends JpaRepository<Academy, Long>, JpaSpecificationExecutor<Academy> {

    Optional<Academy> findBySubdomain(String subdomain);

    Optional<Academy> findByUuid(UUID uuid);

    List<Academy> findByIsActiveTrue();

    List<Academy> findByIsActiveTrueAndSubscriptionEndBefore(LocalDateTime date);

    List<Academy> findByIsActiveTrueAndSubscriptionEndBetween(LocalDateTime from, LocalDateTime to);

    boolean existsBySubdomain(String subdomain);

    /** Busca academia cuyo config JSONB contenga customDomain = :domain */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT * FROM academies WHERE is_active = true AND config ->> 'customDomain' = :domain",
            nativeQuery = true)
    Optional<Academy> findByCustomDomain(@org.springframework.data.repository.query.Param("domain") String domain);
}
