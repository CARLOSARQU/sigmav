-- V6__create_course_teachers.sql

CREATE TABLE course_teachers (
    id              BIGSERIAL       PRIMARY KEY,
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    course_id       BIGINT          NOT NULL REFERENCES courses(id),
    teacher_id      BIGINT          NOT NULL REFERENCES users(id),
    can_create_exams BOOLEAN        NOT NULL DEFAULT FALSE,
    assigned_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_course_teacher UNIQUE (course_id, teacher_id)
);

CREATE INDEX idx_course_teachers_academy_id ON course_teachers(academy_id);
CREATE INDEX idx_course_teachers_course_id ON course_teachers(course_id);
CREATE INDEX idx_course_teachers_teacher_id ON course_teachers(teacher_id);
