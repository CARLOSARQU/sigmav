package com.sigmav.sigmavbackend.modules.exam.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "exams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    /** CYCLE | AREA | COURSE | SIMULACRO */
    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** DRAFT | PUBLISHED | FINISHED | GRADED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    /** Hora de inicio del horario permitido (TIME del día) */
    @Column(name = "available_from")
    private LocalTime availableFrom;

    /** Hora de fin del horario permitido (TIME del día) */
    @Column(name = "available_until")
    private LocalTime availableUntil;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 60;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 1;

    @Column(name = "passing_score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal passingScore = new BigDecimal("50.00");

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (status == null) status = "DRAFT";
        if (isPublished == null) isPublished = false;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
