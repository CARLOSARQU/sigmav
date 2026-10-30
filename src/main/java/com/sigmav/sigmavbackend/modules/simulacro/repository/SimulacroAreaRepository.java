package com.sigmav.sigmavbackend.modules.simulacro.repository;

import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimulacroAreaRepository extends JpaRepository<SimulacroArea, Long> {

    List<SimulacroArea> findByUniversityIdOrderByName(Long universityId);

    List<SimulacroArea> findByUniversityIdAndIsActiveTrueOrderByName(Long universityId);

    List<SimulacroArea> findByUniversityIdInAndIsActiveTrue(Collection<Long> universityIds);

    Optional<SimulacroArea> findByIdAndAcademyId(Long id, Long academyId);
}
