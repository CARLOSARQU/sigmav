package com.sigmav.sigmavbackend.modules.exam.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** SINGLE_CHOICE | MULTIPLE_CHOICE | TEXT | FILE_UPLOAD */
    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    /** Opciones para SINGLE_CHOICE y MULTIPLE_CHOICE. Ejm: ["París", "Londres", "Roma"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    /**
     * Respuesta(s) correcta(s) en formato lista para uniformidad:
     * - SINGLE_CHOICE:   ["París"]
     * - MULTIPLE_CHOICE: ["París", "Roma"]
     * - TEXT/FILE_UPLOAD: null (calificación manual)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", columnDefinition = "jsonb")
    private List<String> correctAnswer;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal points = BigDecimal.ONE;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "requires_manual_grading", nullable = false)
    @Builder.Default
    private Boolean requiresManualGrading = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        // TEXT y FILE_UPLOAD siempre requieren calificación manual
        if (requiresManualGrading == null) {
            requiresManualGrading = "TEXT".equals(questionType) || "FILE_UPLOAD".equals(questionType);
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
