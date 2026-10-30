package com.sigmav.sigmavbackend.modules.session.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "class_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false)
    private String title;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** NONE | BUTTON | QR */
    @Column(name = "attendance_mode", nullable = false, length = 20)
    @Builder.Default
    private String attendanceMode = "NONE";

    /** Token único para el QR — se genera cuando attendanceMode = QR */
    @Column(name = "qr_token", length = 255)
    private String qrToken;

    @Column(name = "attendance_open_from")
    private LocalDateTime attendanceOpenFrom;

    @Column(name = "attendance_open_until")
    private LocalDateTime attendanceOpenUntil;

    /** Minutos de tolerancia para marcar PRESENTE vs TARDE. null = usa el default de la academia (10 min) */
    @Column(name = "attendance_tolerance")
    private Integer attendanceTolerance;

    @Column(name = "meeting_url", columnDefinition = "TEXT")
    private String meetingUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (isActive == null) isActive = true;
        if (attendanceMode == null) attendanceMode = "NONE";
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
