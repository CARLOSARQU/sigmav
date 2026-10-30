package com.sigmav.sigmavbackend.modules.guardian.repository;

import com.sigmav.sigmavbackend.modules.guardian.entity.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findByStudentIdAndIsActiveTrue(Long studentId);

    List<Guardian> findByStudentId(Long studentId);

    List<Guardian> findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(Long academyId);

    @Modifying
    @Transactional
    void deleteByStudentIdAndAcademyId(Long studentId, Long academyId);

    long countByAcademyId(Long academyId);
}
