-- V8__create_sessions_attendance.sql

CREATE TABLE class_sessions (
    id                      BIGSERIAL       PRIMARY KEY,
    uuid                    UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id              BIGINT          NOT NULL REFERENCES academies(id),
    course_id               BIGINT          NOT NULL REFERENCES courses(id),
    teacher_id              BIGINT          NOT NULL REFERENCES users(id),
    title                   VARCHAR(255)    NOT NULL,
    session_date            DATE            NOT NULL,
    start_time              TIME            NOT NULL,
    end_time                TIME            NOT NULL,
    attendance_mode         VARCHAR(20)     NOT NULL DEFAULT 'NONE',
    qr_token                VARCHAR(255)    UNIQUE,
    attendance_open_from    TIMESTAMP,
    attendance_open_until   TIMESTAMP,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_attendance_mode CHECK (attendance_mode IN ('NONE', 'BUTTON', 'QR'))
);

CREATE TABLE attendance_records (
    id          BIGSERIAL       PRIMARY KEY,
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    session_id  BIGINT          NOT NULL REFERENCES class_sessions(id),
    student_id  BIGINT          NOT NULL REFERENCES students(id),
    check_in_at TIMESTAMP,
    check_out_at TIMESTAMP,
    method      VARCHAR(20)     NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ABSENT',

    CONSTRAINT uq_attendance_session_student UNIQUE (session_id, student_id),
    CONSTRAINT chk_attendance_method CHECK (method IN ('QR', 'BUTTON', 'MANUAL')),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT'))
);

CREATE INDEX idx_class_sessions_academy_id ON class_sessions(academy_id);
CREATE INDEX idx_class_sessions_course_id ON class_sessions(course_id);
CREATE INDEX idx_class_sessions_teacher_id ON class_sessions(teacher_id);
CREATE INDEX idx_class_sessions_date ON class_sessions(session_date);
CREATE INDEX idx_attendance_academy_id ON attendance_records(academy_id);
CREATE INDEX idx_attendance_session_id ON attendance_records(session_id);
CREATE INDEX idx_attendance_student_id ON attendance_records(student_id);
