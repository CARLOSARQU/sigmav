package com.sigmav.sigmavbackend.modules.simulacro.repository;

import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroCareer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimulacroCareerRepository extends JpaRepository<SimulacroCareer, Long> {

    List<SimulacroCareer> findByAreaIdOrderByName(Long areaId);

    List<SimulacroCareer> findByAreaIdAndIsActiveTrueOrderByName(Long areaId);

    List<SimulacroCareer> findByAreaIdInAndIsActiveTrue(Collection<Long> areaIds);

    Optional<SimulacroCareer> findByIdAndAcademyId(Long id, Long academyId);

    @Query("""
            SELECT c FROM SimulacroCareer c
            JOIN SimulacroArea a ON a.id = c.areaId
            WHERE a.universityId = :universityId
              AND a.academyId = :academyId
              AND c.isActive = true
            ORDER BY c.name
            """)
    List<SimulacroCareer> findByUniversityIdAndAcademyId(@Param("universityId") Long universityId,
                                                         @Param("academyId") Long academyId);
}
