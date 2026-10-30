package com.sigmav.sigmavbackend.modules.announcement.repository;

import com.sigmav.sigmavbackend.modules.announcement.entity.CourseAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, Long> {

    List<CourseAnnouncement> findByCourseIdAndIsActiveTrueOrderByCreatedAtDesc(Long courseId);

    Optional<CourseAnnouncement> findByIdAndAcademyId(Long id, Long academyId);
}
