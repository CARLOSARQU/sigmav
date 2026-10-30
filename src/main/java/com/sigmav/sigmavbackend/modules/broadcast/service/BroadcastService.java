package com.sigmav.sigmavbackend.modules.broadcast.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.broadcast.dto.AdminBroadcastRequest;
import com.sigmav.sigmavbackend.modules.broadcast.dto.SuperAdminBroadcastRequest;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BroadcastService {

    private static final String BROADCAST_TYPE = "BROADCAST";

    private final NotificationService notificationService;
    private final UserRepository      userRepository;
    private final StudentRepository   studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AreaRepository      areaRepository;
    private final CourseRepository    courseRepository;
    private final AcademyRepository   academyRepository;

    /** Difusión del admin dentro de su propia academia */
    @Transactional
    public int sendAdminBroadcast(AdminBroadcastRequest req) {
        Long academyId = TenantContext.getCurrentAcademyId();

        List<Long> userIds = switch (req.recipients()) {
            case "STUDENTS" -> userRepository
                    .findByAcademyIdAndRoleAndIsActiveTrue(academyId, "STUDENT")
                    .stream().map(User::getId).toList();

            case "TEACHERS" -> userRepository
                    .findByAcademyIdAndRoleAndIsActiveTrue(academyId, "TEACHER")
                    .stream().map(User::getId).toList();

            case "ALL" -> userRepository
                    .findByAcademyIdAndIsActiveTrue(academyId)
                    .stream()
                    .filter(u -> !"SUPER_ADMIN".equals(u.getRole()))
                    .map(User::getId).toList();

            case "CYCLE" -> resolveUserIdsByCycle(req.cycleId(), academyId);

            default -> List.of();
        };

        notificationService.createBatchForUsers(userIds, academyId, BROADCAST_TYPE, req.title(), req.message());
        return userIds.size();
    }

    /** Difusión del superadmin — una o todas las academias activas */
    @Transactional
    public int sendSuperAdminBroadcast(SuperAdminBroadcastRequest req) {
        if ("SPECIFIC".equals(req.targetAcademies()) && req.academyId() == null) {
            throw new IllegalArgumentException("academyId es requerido cuando targetAcademies es SPECIFIC");
        }

        List<Academy> targets = "ALL".equals(req.targetAcademies())
                ? academyRepository.findByIsActiveTrue()
                : List.of(academyRepository.findById(req.academyId())
                        .orElseThrow(() -> new NotFoundException("Academia no encontrada: " + req.academyId())));

        int total = 0;
        for (Academy academy : targets) {
            List<Long> userIds = switch (req.recipients()) {
                case "ADMINS" -> userRepository
                        .findByAcademyIdAndRoleAndIsActiveTrue(academy.getId(), "ACADEMY_ADMIN")
                        .stream().map(User::getId).toList();

                case "ALL" -> userRepository
                        .findByAcademyIdAndIsActiveTrue(academy.getId())
                        .stream()
                        .filter(u -> !"SUPER_ADMIN".equals(u.getRole()))
                        .map(User::getId).toList();

                default -> List.of();
            };

            notificationService.createBatchForUsers(userIds, academy.getId(), BROADCAST_TYPE, req.title(), req.message());
            total += userIds.size();
        }
        return total;
    }

    private List<Long> resolveUserIdsByCycle(Long cycleId, Long academyId) {
        if (cycleId == null) return List.of();

        List<Area> areas = areaRepository.findByCycleIdAndAcademyIdOrderByName(cycleId, academyId);
        if (areas.isEmpty()) return List.of();

        Set<Long> areaIds = areas.stream().map(Area::getId).collect(Collectors.toSet());
        List<Course> courses = courseRepository.findByAreaIdInAndAcademyIdAndIsActiveTrue(
                areaIds.stream().toList(), academyId);
        Set<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());

        List<Long> studentIds = enrollmentRepository.findStudentIdsByAreasOrCourses(areaIds, courseIds);
        if (studentIds.isEmpty()) return List.of();

        return studentRepository.findUserIdsByStudentIds(studentIds);
    }
}
