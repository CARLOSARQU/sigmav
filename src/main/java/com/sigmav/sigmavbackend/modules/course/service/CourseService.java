package com.sigmav.sigmavbackend.modules.course.service;

import com.sigmav.sigmavbackend.modules.area.service.AreaService;
import com.sigmav.sigmavbackend.modules.course.dto.CourseResponse;
import com.sigmav.sigmavbackend.modules.course.dto.CreateCourseRequest;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateCourseRequest;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.entity.CourseTeacher;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseTeacherRepository;
import com.sigmav.sigmavbackend.modules.teacher.dto.AssignTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.dto.CourseTeacherResponse;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final AreaService areaService;
    private final UserRepository userRepository;

    public List<CourseResponse> listByArea(Long areaId, boolean includeInactive, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        List<Course> courses;
        if ("TEACHER".equals(principal.getRole())) {
            // B4.9: TEACHER solo ve sus cursos asignados (siempre activos)
            courses = courseRepository.findByAreaIdAndAcademyIdAndTeacherId(areaId, academyId, principal.getId());
        } else if (includeInactive) {
            // ACADEMY_ADMIN solicitando todos los cursos incluyendo inactivos
            courses = courseRepository.findByAreaIdAndAcademyIdOrderByName(areaId, academyId);
        } else {
            courses = courseRepository.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(areaId, academyId);
        }
        return courses.stream().map(this::toResponse).toList();
    }

    public CourseResponse getById(Long id, UserPrincipal principal) {
        Course course = findOrFail(id);
        assertCourseAccess(course.getId(), principal);
        return toResponse(course);
    }

    @Transactional
    public CourseResponse create(Long areaId, CreateCourseRequest request) {
        areaService.findOrFail(areaId);

        Course course = Course.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .areaId(areaId)
                .name(request.name())
                .description(request.description())
                .build();

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(Long id, UpdateCourseRequest request) {
        Course course = findOrFail(id);

        if (request.name() != null) course.setName(request.name());
        if (request.description() != null) course.setDescription(request.description());
        if (request.isActive() != null) course.setIsActive(request.isActive());

        return toResponse(courseRepository.save(course));
    }

    // --- Gestión de asignaciones docente-curso (B4.8) ---

    public List<CourseTeacherResponse> listTeachers(Long courseId) {
        Course course = findOrFail(courseId);
        return courseTeacherRepository.findByCourseId(courseId)
                .stream().map(ct -> toCourseTeacherResponse(ct, null, course.getName())).toList();
    }

    @Transactional
    public CourseTeacherResponse assignTeacher(Long courseId, Long teacherId, AssignTeacherRequest request) {
        findOrFail(courseId);

        User teacher = userRepository.findById(teacherId)
                .filter(u -> "TEACHER".equals(u.getRole()))
                .orElseThrow(() -> new NotFoundException("Docente no encontrado con ID: " + teacherId));

        if (courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)) {
            throw new IllegalArgumentException("El docente ya está asignado a este curso");
        }

        CourseTeacher ct = CourseTeacher.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .courseId(courseId)
                .teacherId(teacherId)
                .canCreateExams(request != null && request.canCreateExams() != null
                        ? request.canCreateExams() : false)
                .build();

        courseTeacherRepository.save(ct);
        Course course = findOrFail(courseId);
        return toCourseTeacherResponse(ct, teacher.getFullName(), course.getName());
    }

    @Transactional
    public CourseTeacherResponse updateTeacherPermission(Long courseId, Long teacherId, AssignTeacherRequest request) {
        CourseTeacher ct = courseTeacherRepository.findByCourseIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new NotFoundException("El docente no está asignado a este curso"));

        if (request.canCreateExams() != null) {
            ct.setCanCreateExams(request.canCreateExams());
        }

        courseTeacherRepository.save(ct);
        return toCourseTeacherResponse(ct, null, null);
    }

    @Transactional
    public void removeTeacher(Long courseId, Long teacherId) {
        if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)) {
            throw new NotFoundException("El docente no está asignado a este curso");
        }
        courseTeacherRepository.deleteByCourseIdAndTeacherId(courseId, teacherId);
    }

    // --- Helpers ---

    /**
     * B4.9 — Un TEACHER solo puede operar sobre cursos en los que está asignado.
     */
    public void assertCourseAccess(Long courseId, UserPrincipal principal) {
        if ("TEACHER".equals(principal.getRole())) {
            if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, principal.getId())) {
                throw new AccessDeniedException("No tiene acceso a este curso");
            }
        }
    }

    public Course findOrFail(Long id) {
        return courseRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Curso no encontrado con ID: " + id));
    }

    private CourseResponse toResponse(Course c) {
        return new CourseResponse(c.getId(), c.getUuid(), c.getAcademyId(), c.getAreaId(),
                c.getName(), c.getDescription(), c.getIsActive(), c.getCreatedAt());
    }

    public List<CourseTeacherResponse> listByTeacher(Long teacherId) {
        return courseTeacherRepository.findByTeacherId(teacherId)
                .stream().map(ct -> {
                    String courseName = courseRepository.findById(ct.getCourseId())
                            .map(Course::getName).orElse(null);
                    return toCourseTeacherResponse(ct, null, courseName);
                }).toList();
    }

    private CourseTeacherResponse toCourseTeacherResponse(CourseTeacher ct, String teacherName, String courseName) {
        return new CourseTeacherResponse(ct.getId(), ct.getCourseId(), ct.getTeacherId(),
                teacherName, courseName, ct.getCanCreateExams(), ct.getAssignedAt());
    }
}
