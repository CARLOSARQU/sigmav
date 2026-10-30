-- V7__create_enrollments.sql

CREATE TABLE enrollments (
    id              BIGSERIAL       PRIMARY KEY,
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    student_id      BIGINT          NOT NULL REFERENCES students(id),
    enrollment_type VARCHAR(20)     NOT NULL,
    area_id         BIGINT          REFERENCES areas(id),
    course_id       BIGINT          REFERENCES courses(id),
    amount_paid     DECIMAL(10,2),
    currency        VARCHAR(10)     DEFAULT 'PEN',
    enrolled_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_enrollment_type CHECK (enrollment_type IN ('AREA', 'COURSE')),
    CONSTRAINT chk_enrollment_target CHECK (
        (enrollment_type = 'AREA' AND area_id IS NOT NULL) OR
        (enrollment_type = 'COURSE' AND course_id IS NOT NULL)
    )
);

CREATE INDEX idx_enrollments_academy_id ON enrollments(academy_id);
CREATE INDEX idx_enrollments_student_id ON enrollments(student_id);
CREATE INDEX idx_enrollments_area_id ON enrollments(area_id);
CREATE INDEX idx_enrollments_course_id ON enrollments(course_id);
CREATE INDEX idx_enrollments_active ON enrollments(student_id, is_active);
