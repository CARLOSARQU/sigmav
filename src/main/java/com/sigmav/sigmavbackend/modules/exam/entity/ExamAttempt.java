package com.sigmav.sigmavbackend.modules.exam.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "exam_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    /** IN_PROGRESS | SUBMITTED | GRADED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "IN_PROGRESS";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * Estructura:
     * {
     *   "answers":  {"questionId": "respuesta" | ["op1","op2"]},
     *   "manualGrades":   {"questionId": 5.0},
     *   "manualFeedback": {"questionId": "Buen análisis"}
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> answers;

    @Column(name = "auto_score", precision = 5, scale = 2)
    private BigDecimal autoScore;

    @Column(name = "manual_score", precision = 5, scale = 2)
    private BigDecimal manualScore;

    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    private Boolean passed;

    @Column(name = "graded_by")
    private Long gradedBy;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    /** Número de veces que el estudiante salió de la pantalla durante el examen */
    @Column(name = "focus_lost_count", nullable = false)
    @Builder.Default
    private Integer focusLostCount = 0;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (status == null) status = "IN_PROGRESS";
        startedAt = LocalDateTime.now();
    }
}
