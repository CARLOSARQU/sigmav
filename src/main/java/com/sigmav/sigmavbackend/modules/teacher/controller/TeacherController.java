package com.sigmav.sigmavbackend.modules.teacher.controller;

import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.dto.DniLookupResponse;
import com.sigmav.sigmavbackend.modules.student.service.DniLookupService;
import com.sigmav.sigmavbackend.modules.teacher.dto.CourseTeacherResponse;
import com.sigmav.sigmavbackend.modules.teacher.dto.CreateTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.dto.TeacherResponse;
import com.sigmav.sigmavbackend.modules.teacher.dto.UpdateTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.service.TeacherService;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService  teacherService;
    private final CourseService   courseService;
    private final DniLookupService dniLookupService;
    private final StorageService  storageService;
    private final UserRepository  userRepository;

    /** DNI lookup — ASSIGN_TEACHERS or MANAGE_ACADEMY_CONFIG can look up teachers by DNI. */
    @GetMapping("/lookup-dni")
    @PreAuthorize("hasAnyAuthority('PERMISSION_ASSIGN_TEACHERS', 'PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<DniLookupResponse> lookupDni(@RequestParam String dni) {
        return ResponseEntity.ok(dniLookupService.lookup(dni));
    }

    /** List all teachers — ASSIGN_TEACHERS (coordinator) or MANAGE_ACADEMY_CONFIG (admin). */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_ASSIGN_TEACHERS', 'PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<List<TeacherResponse>> listAll() {
        return ResponseEntity.ok(teacherService.listAll());
    }

    /** Get teacher by id — ASSIGN_TEACHERS (coordinator) or MANAGE_ACADEMY_CONFIG (admin). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERMISSION_ASSIGN_TEACHERS', 'PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<TeacherResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getById(id));
    }

    /** Create a new teacher — requires MANAGE_ACADEMY_CONFIG permission. */
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<TeacherResponse> create(@Valid @RequestBody CreateTeacherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teacherService.create(request));
    }

    /** Update teacher profile — requires MANAGE_ACADEMY_CONFIG permission. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<TeacherResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateTeacherRequest request) {
        return ResponseEntity.ok(teacherService.update(id, request));
    }

    /** Deactivate a teacher — requires MANAGE_ACADEMY_CONFIG permission. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        teacherService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** Upload teacher avatar — requires MANAGE_ACADEMY_CONFIG permission. */
    @PostMapping("/{id}/avatar")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<TeacherResponse> uploadAvatar(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        User teacher = teacherService.findOrFail(id);
        try {
            String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                    : ".jpg";
            String key = "avatars/teachers/" + teacher.getUuid() + ext;
            String url = storageService.uploadLogo(key, file.getBytes(), file.getContentType());
            teacher.setAvatarUrl(url);
            userRepository.save(teacher);
        } catch (Exception e) {
            throw new IllegalStateException("Error al subir la imagen: " + e.getMessage());
        }
        return ResponseEntity.ok(teacherService.getById(id));
    }

    /** List courses taught by a teacher — ASSIGN_TEACHERS (coordinator) or MANAGE_ACADEMY_CONFIG (admin). */
    @GetMapping("/{id}/courses")
    @PreAuthorize("hasAnyAuthority('PERMISSION_ASSIGN_TEACHERS', 'PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<List<CourseTeacherResponse>> listCourses(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.listByTeacher(id));
    }
}
