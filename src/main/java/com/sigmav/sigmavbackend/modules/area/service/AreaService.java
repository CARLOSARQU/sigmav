package com.sigmav.sigmavbackend.modules.area.service;

import com.sigmav.sigmavbackend.modules.area.dto.AreaResponse;
import com.sigmav.sigmavbackend.modules.area.dto.CreateAreaRequest;
import com.sigmav.sigmavbackend.modules.area.dto.UpdateAreaRequest;
import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.cycle.service.CycleService;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaRepository areaRepository;
    private final CycleService cycleService;

    public List<AreaResponse> listByCycle(Long cycleId) {
        Long academyId = TenantContext.getCurrentAcademyId();
        return areaRepository.findByCycleIdAndAcademyIdOrderByName(cycleId, academyId)
                .stream().map(this::toResponse).toList();
    }

    public AreaResponse getById(Long id) {
        return toResponse(findOrFail(id));
    }

    @Transactional
    public AreaResponse create(Long cycleId, CreateAreaRequest request) {
        cycleService.findOrFail(cycleId); // valida que el ciclo existe y pertenece a la academia

        Area area = Area.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .cycleId(cycleId)
                .name(request.name())
                .description(request.description())
                .build();

        return toResponse(areaRepository.save(area));
    }

    @Transactional
    public AreaResponse update(Long id, UpdateAreaRequest request) {
        Area area = findOrFail(id);

        if (request.name() != null) area.setName(request.name());
        if (request.description() != null) area.setDescription(request.description());
        if (request.isActive() != null) area.setIsActive(request.isActive());

        return toResponse(areaRepository.save(area));
    }

    @Transactional
    public void deactivate(Long id) {
        Area area = findOrFail(id);
        area.setIsActive(false);
        areaRepository.save(area);
    }

    public Area findOrFail(Long id) {
        return areaRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + id));
    }

    private AreaResponse toResponse(Area a) {
        return new AreaResponse(a.getId(), a.getUuid(), a.getAcademyId(), a.getCycleId(),
                a.getName(), a.getDescription(), a.getIsActive(), a.getCreatedAt());
    }
}
