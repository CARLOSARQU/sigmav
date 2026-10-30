package com.sigmav.sigmavbackend.modules.student.repository;

import com.sigmav.sigmavbackend.modules.student.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    boolean existsByDniAndAcademyId(String dni, Long academyId);

    boolean existsByDniAndAcademyIdAndIdNot(String dni, Long academyId, Long excludeId);

    boolean existsByCodeAndAcademyId(String code, Long academyId);

    /** Búsqueda paginada por nombre, apellido, email o DNI, filtrada por academia. */
    @Query("""
        SELECT s FROM Student s
        WHERE s.academyId = :academyId
        AND (LOWER(CONCAT(s.firstName, ' ', s.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(CAST(s.email AS String)) LIKE LOWER(CONCAT('%', :search, '%'))
            OR s.dni LIKE CONCAT('%', :search, '%'))
        AND (:isActive IS NULL OR s.isActive = :isActive)
        ORDER BY s.lastName, s.firstName
        """)
    Page<Student> searchStudentsWithFilter(@Param("academyId") Long academyId,
                                           @Param("search") String search,
                                           @Param("isActive") Boolean isActive,
                                           Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        WHERE s.academyId = :academyId
        AND (:isActive IS NULL OR s.isActive = :isActive)
        ORDER BY s.lastName, s.firstName
        """)
    Page<Student> searchStudentsNoFilter(@Param("academyId") Long academyId,
                                         @Param("isActive") Boolean isActive,
                                         Pageable pageable);

    Optional<Student> findByIdAndAcademyId(Long id, Long academyId);

    /** Total de estudiantes en la academia (para generar código secuencial) */
    long countByAcademyId(Long academyId);

    /** B8.7 — estudiantes activos en la academia */
    long countByAcademyIdAndIsActiveTrue(Long academyId);

    /** Engagement — todos los estudiantes activos de la academia (sin paginación) */
    List<Student> findByAcademyIdAndIsActiveTrue(Long academyId);

    /** Convierte IDs de Student (PK) → IDs de User (FK) para notificaciones */
    @Query("SELECT s.userId FROM Student s WHERE s.id IN :studentIds AND s.userId IS NOT NULL")
    List<Long> findUserIdsByStudentIds(@Param("studentIds") List<Long> studentIds);

    /** Busca cualquier estudiante con ese DNI en cualquier academia — para autocomplete sin llamar a la API */
    @Query("SELECT s FROM Student s WHERE s.dni = :dni ORDER BY s.id ASC")
    List<Student> findAnyByDni(@Param("dni") String dni);

    /** Búsqueda sin filtro de tenant — para el endpoint público de verificación de carnet */
    @Query("SELECT s FROM Student s WHERE s.uuid = :uuid")
    Optional<Student> findByUuid(@Param("uuid") java.util.UUID uuid);
}
