package com.sigmav.sigmavbackend.modules.simulacro.repository;

import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroUniversity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SimulacroUniversityRepository extends JpaRepository<SimulacroUniversity, Long> {

    List<SimulacroUniversity> findByAcademyIdOrderByName(Long academyId);

    List<SimulacroUniversity> findByAcademyIdAndIsActiveTrueOrderByName(Long academyId);

    Optional<SimulacroUniversity> findByIdAndAcademyId(Long id, Long academyId);
}
