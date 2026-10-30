package com.sigmav.sigmavbackend.modules.course.repository;

import com.sigmav.sigmavbackend.modules.course.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    List<CourseSection> findByCourseIdAndIsActiveTrueOrderByOrderIndex(Long courseId);
    Optional<CourseSection> findByIdAndAcademyId(Long id, Long academyId);
}
