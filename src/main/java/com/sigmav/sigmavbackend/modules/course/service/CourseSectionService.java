package com.sigmav.sigmavbackend.modules.course.service;

import com.sigmav.sigmavbackend.modules.course.dto.CreateSectionRequest;
import com.sigmav.sigmavbackend.modules.course.dto.SectionResponse;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateSectionRequest;
import com.sigmav.sigmavbackend.modules.course.entity.CourseSection;
import com.sigmav.sigmavbackend.modules.course.repository.CourseSectionRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseSectionService {

    private final CourseSectionRepository sectionRepository;
    private final CourseService courseService;

    public List<SectionResponse> listByCourse(Long courseId, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);
        return sectionRepository.findByCourseIdAndIsActiveTrueOrderByOrderIndex(courseId)
                .stream().map(this::toResponse).toList();
    }

    public SectionResponse getById(Long id, UserPrincipal principal) {
        CourseSection section = findOrFail(id);
        courseService.assertCourseAccess(section.getCourseId(), principal);
        return toResponse(section);
    }

    @Transactional
    public SectionResponse create(Long courseId, CreateSectionRequest request, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);

        CourseSection section = CourseSection.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .courseId(courseId)
                .title(request.title())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .build();

        return toResponse(sectionRepository.save(section));
    }

    @Transactional
    public SectionResponse update(Long id, UpdateSectionRequest request, UserPrincipal principal) {
        CourseSection section = findOrFail(id);
        courseService.assertCourseAccess(section.getCourseId(), principal);

        if (request.title() != null) section.setTitle(request.title());
        if (request.orderIndex() != null) section.setOrderIndex(request.orderIndex());
        if (request.isActive() != null) section.setIsActive(request.isActive());

        return toResponse(sectionRepository.save(section));
    }

    public CourseSection findOrFail(Long id) {
        return sectionRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Sección no encontrada con ID: " + id));
    }

    private SectionResponse toResponse(CourseSection s) {
        return new SectionResponse(s.getId(), s.getUuid(), s.getCourseId(),
                s.getTitle(), s.getOrderIndex(), s.getIsActive(), s.getCreatedAt());
    }
}
