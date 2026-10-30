package com.sigmav.sigmavbackend.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(
    name = "course_teachers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "teacher_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "can_create_exams", nullable = false)
    private Boolean canCreateExams;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    protected void onCreate() {
        if (canCreateExams == null) canCreateExams = false;
        assignedAt = LocalDateTime.now();
    }
}
