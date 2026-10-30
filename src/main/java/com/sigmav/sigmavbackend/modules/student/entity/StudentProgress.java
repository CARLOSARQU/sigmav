package com.sigmav.sigmavbackend.modules.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "student_progress")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @PrePersist
    protected void onCreate() {
        viewedAt = LocalDateTime.now();
    }
}
