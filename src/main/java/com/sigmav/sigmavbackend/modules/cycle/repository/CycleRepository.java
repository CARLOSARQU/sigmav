package com.sigmav.sigmavbackend.modules.cycle.repository;

import com.sigmav.sigmavbackend.modules.cycle.entity.Cycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CycleRepository extends JpaRepository<Cycle, Long> {
    List<Cycle> findAllByOrderByStartDateDesc();
    List<Cycle> findByIsActiveTrueOrderByStartDateDesc();
    List<Cycle> findByAcademyIdOrderByStartDateDesc(Long academyId);
    List<Cycle> findByAcademyIdAndIsActiveTrueOrderByStartDateDesc(Long academyId);
    Optional<Cycle> findByIdAndAcademyId(Long id, Long academyId);
}
