package com.sigmav.sigmavbackend.modules.cycle.service;

import com.sigmav.sigmavbackend.modules.cycle.dto.CreateCycleRequest;
import com.sigmav.sigmavbackend.modules.cycle.dto.CycleResponse;
import com.sigmav.sigmavbackend.modules.cycle.dto.UpdateCycleRequest;
import com.sigmav.sigmavbackend.modules.cycle.entity.Cycle;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CycleService {

    private final CycleRepository cycleRepository;

    public List<CycleResponse> listAll() {
        return cycleRepository.findByAcademyIdOrderByStartDateDesc(TenantContext.getCurrentAcademyId())
                .stream().map(this::toResponse).toList();
    }

    public CycleResponse getById(Long id) {
        return toResponse(findOrFail(id));
    }

    @Transactional
    public CycleResponse create(CreateCycleRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio");
        }

        Cycle cycle = Cycle.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        return toResponse(cycleRepository.save(cycle));
    }

    @Transactional
    public CycleResponse update(Long id, UpdateCycleRequest request) {
        Cycle cycle = findOrFail(id);

        if (request.name() != null) cycle.setName(request.name());
        if (request.startDate() != null) cycle.setStartDate(request.startDate());
        if (request.endDate() != null) cycle.setEndDate(request.endDate());
        if (request.isActive() != null) cycle.setIsActive(request.isActive());

        return toResponse(cycleRepository.save(cycle));
    }

    @Transactional
    public void deactivate(Long id) {
        Cycle cycle = findOrFail(id);
        cycle.setIsActive(false);
        cycleRepository.save(cycle);
    }

    public Cycle findOrFail(Long id) {
        return cycleRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Ciclo no encontrado con ID: " + id));
    }

    private CycleResponse toResponse(Cycle c) {
        return new CycleResponse(c.getId(), c.getUuid(), c.getAcademyId(),
                c.getName(), c.getStartDate(), c.getEndDate(), c.getIsActive(), c.getCreatedAt());
    }
}
