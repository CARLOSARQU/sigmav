package com.sigmav.sigmavbackend.modules.student.controller;

import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.dto.*;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.DniLookupService;
import com.sigmav.sigmavbackend.modules.student.service.StudentService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService    studentService;
    private final DniLookupService  dniLookupService;
    private final StorageService    storageService;
    private final StudentRepository studentRepository;

    /** DNI lookup — available to anyone who can register or manage students. */
    @GetMapping("/lookup-dni")
    @PreAuthorize("hasAnyAuthority('PERMISSION_REGISTER_STUDENT', 'PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<DniLookupResponse> lookupDni(@RequestParam String dni) {
        return ResponseEntity.ok(dniLookupService.lookup(dni));
    }

    /** List all students — requires MANAGE_STUDENTS permission. */
    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<Page<StudentResponse>> listAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        return ResponseEntity.ok(studentService.listAll(search, isActive, pageable));
    }

    /** Get a student by ID — requires MANAGE_STUDENTS permission. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<StudentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getById(id));
    }

    /** Register a new student — requires REGISTER_STUDENT permission. */
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_REGISTER_STUDENT')")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(request));
    }

    /** Edit student profile — requires MANAGE_STUDENTS permission. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<StudentResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateStudentRequest request) {
        return ResponseEntity.ok(studentService.update(id, request));
    }

    /** Deactivate a student — requires MANAGE_STUDENTS permission. */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        studentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** Reactivate a student — requires MANAGE_STUDENTS permission. */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        studentService.activate(id);
        return ResponseEntity.noContent().build();
    }

    /** Upload student avatar — requires MANAGE_STUDENTS permission. */
    @PostMapping("/{id}/avatar")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<StudentResponse> uploadAvatar(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        Student student = studentService.findOrFail(id);
        try {
            String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                    : ".jpg";
            String key = "avatars/students/" + student.getUuid() + ext;
            String url = storageService.uploadLogo(key, file.getBytes(), file.getContentType());
            student.setAvatarUrl(url);
            studentRepository.save(student);
        } catch (Exception e) {
            throw new IllegalStateException("Error al subir la imagen: " + e.getMessage());
        }
        return ResponseEntity.ok(studentService.getById(id));
    }

    /** Reset student password — requires MANAGE_STUDENTS permission. */
    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        studentService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Replace guardians for a student — requires MANAGE_STUDENTS permission.
     * Empty list clears all guardians; populated list replaces them.
     */
    @PatchMapping("/{id}/guardians")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_STUDENTS')")
    public ResponseEntity<Void> updateGuardians(
            @PathVariable Long id,
            @Valid @RequestBody List<CreateGuardianRequest> guardians,
            @AuthenticationPrincipal UserPrincipal principal) {
        studentService.updateGuardians(id, guardians, principal.getAcademyId());
        return ResponseEntity.noContent().build();
    }
}
