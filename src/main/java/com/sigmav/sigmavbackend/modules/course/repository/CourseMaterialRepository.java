package com.sigmav.sigmavbackend.modules.course.repository;

import com.sigmav.sigmavbackend.modules.course.entity.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Long> {
    List<CourseMaterial> findByCourseIdAndIsActiveTrueOrderByOrderIndex(Long courseId);
    List<CourseMaterial> findBySectionIdAndIsActiveTrueOrderByOrderIndex(Long sectionId);
    Optional<CourseMaterial> findByIdAndAcademyId(Long id, Long academyId);
    // UUID para endpoints públicos — no expone IDs secuenciales
    Optional<CourseMaterial> findByUuidAndAcademyId(UUID uuid, Long academyId);

    /** Engagement — total de materiales activos en la academia (denominador compartido) */
    long countByAcademyIdAndIsActiveTrue(Long academyId);
}
