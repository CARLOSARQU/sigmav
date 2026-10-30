package com.sigmav.sigmavbackend.modules.course.controller;

import com.sigmav.sigmavbackend.modules.course.dto.CreateMaterialRequest;
import com.sigmav.sigmavbackend.modules.course.dto.MaterialResponse;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateMaterialRequest;
import com.sigmav.sigmavbackend.modules.course.service.CourseMaterialService;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.dto.UploadUrlResponse;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CourseMaterialController {

    private final CourseMaterialService materialService;

    /** Lista todos los materiales de un curso */
    @GetMapping("/api/courses/{courseId}/materials")
    public ResponseEntity<List<MaterialResponse>> listByCourse(@PathVariable Long courseId,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.listByCourse(courseId, principal));
    }

    /** Lista materiales de una sección específica */
    @GetMapping("/api/courses/{courseId}/sections/{sectionId}/materials")
    public ResponseEntity<List<MaterialResponse>> listBySection(@PathVariable Long courseId,
                                                                @PathVariable Long sectionId,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.listBySection(sectionId, courseId, principal));
    }

    @GetMapping("/api/materials/{id}")
    public ResponseEntity<MaterialResponse> getById(@PathVariable Long id,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.getById(id, principal));
    }

    @PostMapping("/api/courses/{courseId}/materials")
    public ResponseEntity<MaterialResponse> create(@PathVariable Long courseId,
                                                   @Valid @RequestBody CreateMaterialRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(materialService.create(courseId, request, principal));
    }

    @PatchMapping("/api/materials/{id}")
    public ResponseEntity<MaterialResponse> update(@PathVariable Long id,
                                                   @RequestBody UpdateMaterialRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.update(id, request, principal));
    }

    /**
     * Genera una URL de descarga prefirmada (15 min) para un material PDF_S3.
     * El archivo se sirve directamente desde R2, sin pasar por el backend.
     * Accesible por ACADEMY_ADMIN y TEACHER (con acceso al curso).
     * Para STUDENT, usar GET /api/student/materials/{uuid}/url
     */
    @GetMapping("/api/materials/{uuid}/url")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.generateDownloadUrl(uuid, principal));
    }

    /**
     * Genera una URL de subida prefirmada (10 min) para subir un PDF directamente a R2.
     *
     * Flujo completo de subida:
     *   1. GET /api/admin/storage/upload-url?courseId=5&filename=apuntes.pdf
     *      → Respuesta: { "uploadUrl": "https://...", "key": "1/courses/5/uuid.pdf" }
     *   2. PUT {uploadUrl} con el archivo (desde el frontend)
     *   3. POST /api/courses/5/materials con { "materialType": "PDF_S3", "fileUrl": "1/courses/5/uuid.pdf" }
     */
    @GetMapping("/api/admin/storage/upload-url")
    public ResponseEntity<UploadUrlResponse> getUploadUrl(
            @RequestParam Long courseId,
            @RequestParam String filename,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(materialService.generateUploadUrl(courseId, filename, principal));
    }
}
