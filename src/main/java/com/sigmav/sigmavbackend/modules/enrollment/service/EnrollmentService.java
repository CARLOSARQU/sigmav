package com.sigmav.sigmavbackend.modules.enrollment.service;

import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.area.service.AreaService;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.enrollment.dto.CreateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentFinanceItemResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.UpdateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentStatus;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.StudentService;
import com.sigmav.sigmavbackend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final AreaService areaService;
    private final CourseService courseService;
    private final CourseRepository courseRepository;
    private final AreaRepository areaRepository;

    public List<EnrollmentResponse> listByStudent(Long studentId) {
        studentService.findOrFail(studentId); // valida que el estudiante pertenece a la academia
        return toResponses(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(studentId));
    }

    @Transactional
    public EnrollmentResponse create(CreateEnrollmentRequest request) {
        studentService.findOrFail(request.studentId());

        String type = request.type().toUpperCase();

        switch (type) {
            case "AREA" -> {
                if (request.areaId() == null)
                    throw new IllegalArgumentException("areaId es requerido para matrículas de tipo AREA");
                areaService.findOrFail(request.areaId()); // valida que el área pertenece a la academia
            }
            case "COURSE" -> {
                if (request.courseId() == null)
                    throw new IllegalArgumentException("courseId es requerido para matrículas de tipo COURSE");
                courseService.findOrFail(request.courseId()); // valida que el curso pertenece a la academia
            }
            default -> throw new IllegalArgumentException("Tipo de matrícula inválido: " + request.type() + ". Use AREA o COURSE");
        }

        Enrollment enrollment = Enrollment.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .studentId(request.studentId())
                .enrollmentType(type)
                .areaId("AREA".equals(type) ? request.areaId() : null)
                .courseId("COURSE".equals(type) ? request.courseId() : null)
                .amountPaid(request.amountPaid())
                .totalAmount(request.totalAmount())
                .currency(request.currency() != null ? request.currency() : "PEN")
                .paymentMethod(request.paymentMethod())
                .expiresAt(request.expiresAt())
                .notes(request.notes())
                .build();

        // El estado de pago es derivado: lo que envíe el cliente se ignora.
        enrollment.setPaymentStatus(derivePaymentStatus(
                enrollment.getAmountPaid(), enrollment.getTotalAmount(), enrollment.getExpiresAt()));

        return toResponses(List.of(enrollmentRepository.save(enrollment))).get(0);
    }

    @Transactional
    public EnrollmentResponse update(Long id, UpdateEnrollmentRequest request) {
        Enrollment enrollment = findOrFail(id);
        if (request.amountPaid() != null) enrollment.setAmountPaid(request.amountPaid());
        if (request.totalAmount() != null) enrollment.setTotalAmount(request.totalAmount());
        if (request.currency() != null) enrollment.setCurrency(request.currency());
        if (request.paymentMethod() != null) enrollment.setPaymentMethod(request.paymentMethod());
        if (request.notes() != null) enrollment.setNotes(request.notes());
        // expiresAt siempre se aplica (null = eliminar vencimiento)
        enrollment.setExpiresAt(request.expiresAt());

        // El estado de pago se recalcula sobre los valores ya aplicados.
        enrollment.setPaymentStatus(derivePaymentStatus(
                enrollment.getAmountPaid(), enrollment.getTotalAmount(), enrollment.getExpiresAt()));

        return toResponses(List.of(enrollmentRepository.save(enrollment))).get(0);
    }

    @Transactional
    public void deactivate(Long id) {
        Enrollment enrollment = findOrFail(id);
        enrollment.setIsActive(false);
        enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void activate(Long id) {
        Enrollment enrollment = findOrFail(id);
        enrollment.setIsActive(true);
        enrollmentRepository.save(enrollment);
    }

    public Enrollment findOrFail(Long id) {
        return enrollmentRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Matrícula no encontrada con ID: " + id));
    }

    /**
     * B5.5 — Verifica si un estudiante tiene acceso activo y no vencido a un curso.
     * Acepta matrícula directa por curso o por área del curso.
     */
    public boolean hasAccessToCourse(Long studentId, Long courseId) {
        // 1) Matrícula directa por curso
        if (enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId)) {
            return true;
        }
        // 2) Matrícula por área — necesitamos el areaId del curso
        return courseRepository.findById(courseId)
                .map(Course::getAreaId)
                .map(areaId -> enrollmentRepository.existsActiveAreaEnrollment(studentId, areaId))
                .orElse(false);
    }

    /** Lanza AccessDeniedException si el estudiante no tiene acceso al curso (B5.9) */
    public void assertStudentAccess(Long studentId, Long courseId) {
        if (!hasAccessToCourse(studentId, courseId)) {
            throw new AccessDeniedException("No tiene matrícula activa para este curso");
        }
    }

    public List<EnrollmentFinanceItemResponse> listForFinances(String paymentStatus) {
        Long academyId = TenantContext.getCurrentAcademyId();

        List<Enrollment> enrollments;
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            PaymentStatus ps = PaymentStatus.valueOf(paymentStatus.toUpperCase());
            enrollments = enrollmentRepository.findByAcademyIdAndPaymentStatusOrderByEnrolledAtDesc(academyId, ps);
        } else {
            enrollments = enrollmentRepository.findByAcademyIdOrderByEnrolledAtDesc(academyId);
        }

        Set<Long> studentIds = enrollments.stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toSet());

        Map<Long, Student> studentMap = studentRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        // Los nombres se resuelven en batch: dos consultas para todo el listado,
        // no una por fila y relación (N+1).
        Map<Long, String> areaNames   = areaNames(areaIds(enrollments), academyId);
        Map<Long, String> courseNames = courseNames(courseIds(enrollments), academyId);

        return enrollments.stream()
                .map(e -> toFinanceItem(e, studentMap.get(e.getStudentId()), areaNames, courseNames))
                .toList();
    }

    private EnrollmentFinanceItemResponse toFinanceItem(Enrollment e, Student student,
                                                        Map<Long, String> areaNames,
                                                        Map<Long, String> courseNames) {
        String name = student != null ? student.getFirstName() + " " + student.getLastName() : "—";
        String dni  = student != null ? student.getDni() : "—";
        String pm   = e.getPaymentMethod() != null ? e.getPaymentMethod().name() : null;
        return new EnrollmentFinanceItemResponse(
                e.getId(), e.getStudentId(), name, dni,
                e.getEnrollmentType(),
                e.getAreaId() != null ? areaNames.get(e.getAreaId()) : null,
                e.getCourseId() != null ? courseNames.get(e.getCourseId()) : null,
                e.getAmountPaid(), e.getTotalAmount(), e.getCurrency(), pm,
                e.getPaymentStatus(), e.getNotes(),
                e.getEnrolledAt(), e.getExpiresAt(), e.getIsActive());
    }

    /**
     * Estado de pago derivado de los montos y la fecha de vencimiento. El cliente no lo elige:
     * VENCIDO solo aplica a matrículas impagas con un total acordado, así que una matrícula
     * pagada por completo nunca cae en VENCIDO (eso inflaba el KPI "con deuda" de Finanzas).
     */
    static PaymentStatus derivePaymentStatus(BigDecimal amountPaid, BigDecimal totalAmount, LocalDateTime expiresAt) {
        boolean notFullyPaid = totalAmount != null && totalAmount.signum() > 0
                && (amountPaid == null || amountPaid.compareTo(totalAmount) < 0);

        if (notFullyPaid && expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return PaymentStatus.VENCIDO;
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            return PaymentStatus.PENDIENTE;
        }
        if (amountPaid == null || amountPaid.signum() <= 0) {
            return PaymentStatus.PENDIENTE;
        }
        if (amountPaid.compareTo(totalAmount) >= 0) {
            return PaymentStatus.PAGADO;
        }
        return PaymentStatus.PARCIAL;
    }

    private List<EnrollmentResponse> toResponses(List<Enrollment> enrollments) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Map<Long, String> areaNames   = areaNames(areaIds(enrollments), academyId);
        Map<Long, String> courseNames = courseNames(courseIds(enrollments), academyId);
        return enrollments.stream()
                .map(e -> toResponse(e, areaNames, courseNames))
                .toList();
    }

    private EnrollmentResponse toResponse(Enrollment e, Map<Long, String> areaNames, Map<Long, String> courseNames) {
        return new EnrollmentResponse(e.getId(), e.getAcademyId(), e.getStudentId(),
                e.getEnrollmentType(), e.getAreaId(),
                e.getAreaId() != null ? areaNames.get(e.getAreaId()) : null,
                e.getCourseId(),
                e.getCourseId() != null ? courseNames.get(e.getCourseId()) : null,
                e.getAmountPaid(), e.getTotalAmount(), e.getCurrency(), e.getPaymentMethod(),
                e.getPaymentStatus(), e.getNotes(),
                e.getEnrolledAt(), e.getExpiresAt(), e.getIsActive());
    }

    private Set<Long> areaIds(List<Enrollment> enrollments) {
        return enrollments.stream()
                .map(Enrollment::getAreaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> courseIds(List<Enrollment> enrollments) {
        return enrollments.stream()
                .map(Enrollment::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Nombres de área por id. Nunca lanza excepción por una fila huérfana: el id que no
     * aparece en el mapa se resuelve como null y el listado no se rompe. No filtra por
     * isActive — una matrícula histórica de un área desactivada debe seguir mostrando su nombre.
     */
    private Map<Long, String> areaNames(Set<Long> areaIds, Long academyId) {
        if (areaIds.isEmpty()) return Map.of();
        return areaRepository.findByIdInAndAcademyId(areaIds, academyId).stream()
                .collect(Collectors.toMap(Area::getId, Area::getName));
    }

    /** Nombres de curso por id. Mismas garantías que {@link #areaNames(Set, Long)}. */
    private Map<Long, String> courseNames(Set<Long> courseIds, Long academyId) {
        if (courseIds.isEmpty()) return Map.of();
        return courseRepository.findByIdInAndAcademyId(courseIds, academyId).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
    }
}
