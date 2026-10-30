package com.sigmav.sigmavbackend.modules.enrollment.controller;

import com.sigmav.sigmavbackend.modules.enrollment.dto.CreateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentFinanceItemResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.UpdateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /** Financial view of enrollments — requires READ_FINANCES permission. */
    @GetMapping("/api/enrollments")
    @PreAuthorize("hasAuthority('PERMISSION_READ_FINANCES')")
    public ResponseEntity<List<EnrollmentFinanceItemResponse>> listAll(
            @RequestParam(required = false) String paymentStatus) {
        return ResponseEntity.ok(enrollmentService.listForFinances(paymentStatus));
    }

    /** Create a new enrollment — requires MANAGE_ENROLLMENTS permission. */
    @PostMapping("/api/enrollments")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ENROLLMENTS')")
    public ResponseEntity<EnrollmentResponse> create(@Valid @RequestBody CreateEnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.create(request));
    }

    /** View enrollments for a specific student — requires VIEW_ENROLLMENTS permission. */
    @GetMapping("/api/students/{studentId}/enrollments")
    @PreAuthorize("hasAuthority('PERMISSION_VIEW_ENROLLMENTS')")
    public ResponseEntity<List<EnrollmentResponse>> listByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(enrollmentService.listByStudent(studentId));
    }

    /** Record a payment or update financial fields on an enrollment — requires WRITE_FINANCES permission. */
    @PatchMapping("/api/enrollments/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_WRITE_FINANCES')")
    public ResponseEntity<EnrollmentResponse> update(@PathVariable Long id,
                                                     @RequestBody UpdateEnrollmentRequest request) {
        return ResponseEntity.ok(enrollmentService.update(id, request));
    }

    /** Cancel/deactivate an enrollment — requires MANAGE_ENROLLMENTS permission. */
    @PatchMapping("/api/enrollments/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ENROLLMENTS')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        enrollmentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-activate an enrollment — requires MANAGE_ENROLLMENTS permission. */
    @PatchMapping("/api/enrollments/{id}/activate")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ENROLLMENTS')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        enrollmentService.activate(id);
        return ResponseEntity.noContent().build();
    }
}
