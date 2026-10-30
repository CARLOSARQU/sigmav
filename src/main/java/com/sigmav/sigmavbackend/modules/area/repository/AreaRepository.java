package com.sigmav.sigmavbackend.modules.area.repository;

import com.sigmav.sigmavbackend.modules.area.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<Area, Long> {
    List<Area> findByCycleIdOrderByName(Long cycleId);
    List<Area> findByCycleIdAndIsActiveTrueOrderByName(Long cycleId);
    List<Area> findByCycleIdAndAcademyIdOrderByName(Long cycleId, Long academyId);
    List<Area> findByCycleIdAndAcademyIdAndIsActiveTrueOrderByName(Long cycleId, Long academyId);
    Optional<Area> findByIdAndAcademyId(Long id, Long academyId);

    /** Batch load de áreas para múltiples ciclos — evita N+1 en filtrado de exámenes */
    @Query("SELECT a FROM Area a WHERE a.cycleId IN :cycleIds AND a.academyId = :academyId")
    List<Area> findByCycleIdInAndAcademyId(@Param("cycleIds") Collection<Long> cycleIds,
                                            @Param("academyId") Long academyId);

    /** Batch load de áreas por id — evita N+1 al resolver nombres en listados.
     * No filtra por isActive a propósito: una matrícula histórica de un área
     * desactivada debe seguir mostrando su nombre. */
    @Query("SELECT a FROM Area a WHERE a.id IN :ids AND a.academyId = :academyId")
    List<Area> findByIdInAndAcademyId(@Param("ids") Collection<Long> ids,
                                      @Param("academyId") Long academyId);
}
