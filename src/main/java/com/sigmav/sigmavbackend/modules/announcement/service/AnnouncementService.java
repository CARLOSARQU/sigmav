package com.sigmav.sigmavbackend.modules.announcement.service;

import com.sigmav.sigmavbackend.modules.announcement.dto.AnnouncementResponse;
import com.sigmav.sigmavbackend.modules.announcement.dto.CreateAnnouncementRequest;
import com.sigmav.sigmavbackend.modules.announcement.entity.CourseAnnouncement;
import com.sigmav.sigmavbackend.modules.announcement.repository.CourseAnnouncementRepository;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.notification.service.NotificationService;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final CourseAnnouncementRepository announcementRepo;
    private final CourseRepository courseRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final StudentRepository studentRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public List<AnnouncementResponse> listByCourse(Long courseId) {
        Long academyId = TenantContext.getCurrentAcademyId();
        courseRepo.findByIdAndAcademyId(courseId, academyId)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + courseId));
        return announcementRepo.findByCourseIdAndIsActiveTrueOrderByCreatedAtDesc(courseId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AnnouncementResponse create(Long courseId, CreateAnnouncementRequest request, Long createdBy) {
        Long academyId = TenantContext.getCurrentAcademyId();

        Course course = courseRepo.findByIdAndAcademyId(courseId, academyId)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado: " + courseId));

        CourseAnnouncement announcement = CourseAnnouncement.builder()
                .academyId(academyId)
                .courseId(courseId)
                .createdBy(createdBy)
                .title(request.title())
                .body(request.body())
                .build();

        announcement = announcementRepo.save(announcement);

        // Notificar a los estudiantes matriculados
        notifyStudents(courseId, course.getAreaId(), academyId, request.title(), request.body());

        return toResponse(announcement);
    }

    @Transactional
    public void delete(Long id) {
        Long academyId = TenantContext.getCurrentAcademyId();
        CourseAnnouncement announcement = announcementRepo.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Anuncio no encontrado: " + id));
        announcement.setIsActive(false);
        announcementRepo.save(announcement);
    }

    private void notifyStudents(Long courseId, Long areaId, Long academyId, String title, String body) {
        List<Long> studentIds = enrollmentRepo.findEnrolledStudentIds(courseId, areaId);
        if (studentIds.isEmpty()) return;

        String preview = body.length() > 120 ? body.substring(0, 120) + "…" : body;
        studentRepo.findAllById(studentIds).forEach(s -> {
            if (s.getUserId() != null) {
                notificationService.createForUser(s.getUserId(), academyId, "ANNOUNCEMENT", title, preview);
            }
        });
    }

    private AnnouncementResponse toResponse(CourseAnnouncement a) {
        String authorName = userRepo.findById(a.getCreatedBy())
                .map(User::getFullName).orElse("—");
        return new AnnouncementResponse(
                a.getId(), a.getCourseId(), a.getTitle(), a.getBody(), authorName, a.getCreatedAt());
    }
}
