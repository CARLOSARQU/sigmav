package com.sigmav.sigmavbackend.modules.session.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

@Filter(name = "tenantFilter", condition = "academy_id = :academyId")
@Entity
@Table(name = "attendance_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "academy_id", nullable = false)
    private Long academyId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    /** QR | BUTTON | MANUAL */
    @Column(nullable = false, length = 20)
    private String method;

    /** PRESENT | LATE | ABSENT */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ABSENT";
}
