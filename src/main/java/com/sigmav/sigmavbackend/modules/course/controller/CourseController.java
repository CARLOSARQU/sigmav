package com.sigmav.sigmavbackend.modules.course.controller;

import com.sigmav.sigmavbackend.modules.course.dto.CourseResponse;
import com.sigmav.sigmavbackend.modules.course.dto.CreateCourseRequest;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateCourseRequest;
import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.teacher.dto.AssignTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.dto.CourseTeacherResponse;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** Lista cursos de un área. TEACHER: solo sus cursos asignados (B4.9).
     *  ACADEMY_ADMIN puede pasar includeInactive=true para ver también los inactivos. */
    @GetMapping("/api/areas/{areaId}/courses")
    public ResponseEntity<List<CourseResponse>> listByArea(@PathVariable Long areaId,
                                                           @RequestParam(defaultValue = "false") boolean includeInactive,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(courseService.listByArea(areaId, includeInactive, principal));
    }

    @GetMapping("/api/courses/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(courseService.getById(id, principal));
    }

    @PostMapping("/api/areas/{areaId}/courses")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<CourseResponse> create(@PathVariable Long areaId,
                                                 @Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(areaId, request));
    }

    @PatchMapping("/api/courses/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<CourseResponse> update(@PathVariable Long id,
                                                 @RequestBody UpdateCourseRequest request) {
        return ResponseEntity.ok(courseService.update(id, request));
    }

    // --- Asignaciones docente-curso (B4.8) ---

    @GetMapping("/api/courses/{courseId}/teachers")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN_TEACHERS')")
    public ResponseEntity<List<CourseTeacherResponse>> listTeachers(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.listTeachers(courseId));
    }

    @PostMapping("/api/courses/{courseId}/teachers/{teacherId}")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN_TEACHERS')")
    public ResponseEntity<CourseTeacherResponse> assignTeacher(@PathVariable Long courseId,
                                                               @PathVariable Long teacherId,
                                                               @RequestBody(required = false) AssignTeacherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.assignTeacher(courseId, teacherId, request));
    }

    @PatchMapping("/api/courses/{courseId}/teachers/{teacherId}")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN_TEACHERS')")
    public ResponseEntity<CourseTeacherResponse> updateTeacherPermission(@PathVariable Long courseId,
                                                                         @PathVariable Long teacherId,
                                                                         @RequestBody AssignTeacherRequest request) {
        return ResponseEntity.ok(courseService.updateTeacherPermission(courseId, teacherId, request));
    }

    @DeleteMapping("/api/courses/{courseId}/teachers/{teacherId}")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN_TEACHERS')")
    public ResponseEntity<Void> removeTeacher(@PathVariable Long courseId,
                                              @PathVariable Long teacherId) {
        courseService.removeTeacher(courseId, teacherId);
        return ResponseEntity.noContent().build();
    }
}
