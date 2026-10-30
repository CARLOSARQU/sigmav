package com.sigmav.sigmavbackend.modules.public_portal.controller;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.cycle.entity.Cycle;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.public_.dto.StudentVerifyResponse;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final AcademyRepository    academyRepo;
    private final CycleRepository      cycleRepo;
    private final AreaRepository       areaRepo;
    private final CourseRepository     courseRepo;
    private final StudentRepository    studentRepo;
    private final EnrollmentRepository enrollmentRepo;

    /**
     * Catálogo público de la academia — sin autenticación.
     * Devuelve la info de la academia y sus ciclos/áreas/cursos activos.
     */
    @GetMapping("/{subdomain}/catalog")
    public ResponseEntity<Map<String, Object>> getCatalog(@PathVariable String subdomain) {
        Academy academy = academyRepo.findBySubdomain(subdomain)
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .orElseThrow(() -> new NotFoundException("Academia no encontrada: " + subdomain));

        List<Cycle> cycles = cycleRepo.findByAcademyIdAndIsActiveTrueOrderByStartDateDesc(academy.getId());

        List<Map<String, Object>> cycleList = new ArrayList<>();
        for (Cycle cycle : cycles) {
            List<Area> areas = areaRepo.findByCycleIdAndAcademyIdAndIsActiveTrueOrderByName(
                    cycle.getId(), academy.getId());

            List<Map<String, Object>> areaList = new ArrayList<>();
            for (Area area : areas) {
                List<Course> courses = courseRepo.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(
                        area.getId(), academy.getId());

                List<Map<String, Object>> courseList = courses.stream()
                        .map(c -> Map.<String, Object>of(
                                "id", c.getId(),
                                "name", c.getName(),
                                "description", c.getDescription() != null ? c.getDescription() : ""))
                        .toList();

                areaList.add(Map.of(
                        "id", area.getId(),
                        "name", area.getName(),
                        "description", area.getDescription() != null ? area.getDescription() : "",
                        "courses", courseList));
            }

            cycleList.add(Map.of(
                    "id", cycle.getId(),
                    "name", cycle.getName(),
                    "startDate", cycle.getStartDate().toString(),
                    "endDate", cycle.getEndDate().toString(),
                    "areas", areaList));
        }

        String primaryColor = configStr(academy, "primaryColor", "#6366f1");
        String logoUrl      = configStr(academy, "logoUrl", "");

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("academyName",   academy.getName());
        result.put("subdomain",     academy.getSubdomain());
        result.put("primaryColor",  primaryColor);
        result.put("logoUrl",       logoUrl);
        result.put("cycles",        cycleList);
        return ResponseEntity.ok(result);
    }

    private String configStr(Academy academy, String key, String def) {
        if (academy.getConfig() == null) return def;
        Object v = academy.getConfig().get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    @GetMapping("/verify/{uuid}")
    public ResponseEntity<StudentVerifyResponse> verify(@PathVariable UUID uuid) {
        Student student = studentRepo.findByUuid(uuid).orElse(null);

        if (student == null || !student.getIsActive()) {
            return ResponseEntity.ok(new StudentVerifyResponse(
                    false, null, null, null, null, null, null, null, null));
        }

        String academyName = academyRepo.findById(student.getAcademyId())
                .map(Academy::getName).orElse("—");

        List<Enrollment> areaEnrollments   = enrollmentRepo.findActiveAreaEnrollments(student.getId());
        List<Enrollment> courseEnrollments = enrollmentRepo.findActiveCourseEnrollments(student.getId());
        boolean hasActive = !areaEnrollments.isEmpty() || !courseEnrollments.isEmpty();

        if (!hasActive) {
            return ResponseEntity.ok(new StudentVerifyResponse(
                    false, student.getFullName(), student.getDni(),
                    student.getCode(), student.getAvatarUrl(), academyName,
                    null, null, null));
        }

        Enrollment first = !areaEnrollments.isEmpty() ? areaEnrollments.get(0) : courseEnrollments.get(0);
        String enrolledIn;
        if ("AREA".equals(first.getEnrollmentType())) {
            enrolledIn = areaRepo.findById(first.getAreaId())
                    .map(a -> "Área: " + a.getName()).orElse("—");
        } else {
            enrolledIn = courseRepo.findById(first.getCourseId())
                    .map(c -> "Curso: " + c.getName()).orElse("—");
        }

        return ResponseEntity.ok(new StudentVerifyResponse(
                true, student.getFullName(), student.getDni(),
                student.getCode(), student.getAvatarUrl(), academyName,
                enrolledIn, first.getEnrolledAt(), first.getExpiresAt()));
    }
}
