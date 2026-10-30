package com.sigmav.sigmavbackend.modules.course.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Feature;
import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.modules.course.dto.CreateMaterialRequest;
import com.sigmav.sigmavbackend.modules.course.dto.MaterialResponse;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateMaterialRequest;
import com.sigmav.sigmavbackend.modules.course.entity.CourseMaterial;
import com.sigmav.sigmavbackend.modules.course.repository.CourseMaterialRepository;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.dto.UploadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseMaterialService {

    private static final String MATERIAL_TYPE_R2 = "PDF_S3";

    private final CourseMaterialRepository materialRepository;
    private final CourseService courseService;
    private final AcademyService academyService;
    private final StorageService storageService;

    public List<MaterialResponse> listByCourse(Long courseId, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);
        return materialRepository.findByCourseIdAndIsActiveTrueOrderByOrderIndex(courseId)
                .stream().map(this::toResponse).toList();
    }

    public List<MaterialResponse> listBySection(Long sectionId, Long courseId, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);
        return materialRepository.findBySectionIdAndIsActiveTrueOrderByOrderIndex(sectionId)
                .stream().map(this::toResponse).toList();
    }

    public MaterialResponse getById(Long id, UserPrincipal principal) {
        CourseMaterial material = findOrFail(id);
        courseService.assertCourseAccess(material.getCourseId(), principal);
        return toResponse(material);
    }

    @Transactional
    public MaterialResponse create(Long courseId, CreateMaterialRequest request, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);

        if ("PDF_S3".equals(request.materialType())) {
            academyService.assertFeatureEnabled(Feature.S3_STORAGE);
        }

        CourseMaterial material = CourseMaterial.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .courseId(courseId)
                .sectionId(request.sectionId())
                .title(request.title())
                .materialType(request.materialType())
                .fileUrl(MATERIAL_TYPE_R2.equals(request.materialType()) ? request.fileUrl()
                        : "VIDEO".equals(request.materialType()) ? normalizeVideoUrl(request.fileUrl())
                        : request.fileUrl())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .build();

        return toResponse(materialRepository.save(material));
    }

    @Transactional
    public MaterialResponse update(Long id, UpdateMaterialRequest request, UserPrincipal principal) {
        CourseMaterial material = findOrFail(id);
        courseService.assertCourseAccess(material.getCourseId(), principal);

        if (request.title() != null) material.setTitle(request.title());
        if (request.fileUrl() != null) {
            material.setFileUrl("VIDEO".equals(material.getMaterialType())
                    ? normalizeVideoUrl(request.fileUrl())
                    : request.fileUrl());
        }
        if (request.orderIndex() != null) material.setOrderIndex(request.orderIndex());
        if (request.isActive() != null) material.setIsActive(request.isActive());

        return toResponse(materialRepository.save(material));
    }

    public CourseMaterial findOrFail(Long id) {
        return materialRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Material no encontrado con ID: " + id));
    }

    /**
     * Genera una URL de descarga prefirmada para un material PDF almacenado en R2.
     * Valida que el usuario tiene acceso al curso antes de generar la URL.
     * Usada por ACADEMY_ADMIN y TEACHER.
     */
    public DownloadUrlResponse generateDownloadUrl(String uuid, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        CourseMaterial material = materialRepository
                .findByUuidAndAcademyId(UUID.fromString(uuid), academyId)
                .orElseThrow(() -> new NotFoundException("Material no encontrado"));

        if (!MATERIAL_TYPE_R2.equals(material.getMaterialType())) {
            throw new IllegalArgumentException("Este material no tiene archivo privado en R2");
        }

        courseService.assertCourseAccess(material.getCourseId(), principal);

        String url = storageService.generateDownloadUrl(material.getFileUrl());
        return new DownloadUrlResponse(url, storageService.getExpiryMinutes() * 60);
    }

    /**
     * Genera una URL de subida prefirmada para que el frontend suba el archivo
     * directamente a R2. Usada por ACADEMY_ADMIN y TEACHER antes de crear el material.
     *
     * Flujo:
     *   1. Frontend llama a este endpoint → recibe {uploadUrl, key}
     *   2. Frontend hace PUT al uploadUrl con el archivo
     *   3. Frontend llama a POST /api/courses/{id}/materials con fileUrl = key
     */
    public UploadUrlResponse generateUploadUrl(Long courseId, String filename, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);

        academyService.assertFeatureEnabled(Feature.S3_STORAGE);

        // Key = {academyId}/courses/{courseId}/{uuid}.{ext}
        // Namespacing por academia garantiza que un ADMIN de otra academia
        // nunca pueda adivinar ni acceder a las keys de otra academia.
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        String key = academyId + "/courses/" + courseId + "/" + UUID.randomUUID() + ext;

        return storageService.generateUploadUrl(key, "application/pdf");
    }

    private String normalizeVideoUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.contains("youtube.com/embed/")) {
            return url.split("\\?")[0] + "?rel=0&modestbranding=1";
        }
        if (url.contains("youtu.be/")) {
            String videoId = url.substring(url.lastIndexOf('/') + 1).split("\\?")[0];
            return "https://www.youtube.com/embed/" + videoId + "?rel=0&modestbranding=1";
        }
        if (url.contains("youtube.com/watch")) {
            String videoId = Arrays.stream(url.split("[?&]"))
                    .filter(p -> p.startsWith("v="))
                    .map(p -> p.substring(2))
                    .findFirst().orElse(null);
            if (videoId != null) {
                return "https://www.youtube.com/embed/" + videoId + "?rel=0&modestbranding=1";
            }
        }
        return url;
    }

    private MaterialResponse toResponse(CourseMaterial m) {
        return new MaterialResponse(m.getId(), m.getUuid(), m.getCourseId(), m.getSectionId(),
                m.getTitle(), m.getMaterialType(), m.getFileUrl(), m.getOrderIndex(),
                m.getIsActive(), m.getCreatedAt());
    }
}
