-- V9__create_exams.sql

CREATE TABLE exams (
    id                  BIGSERIAL       PRIMARY KEY,
    uuid                UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id          BIGINT          NOT NULL REFERENCES academies(id),
    scope_type          VARCHAR(20)     NOT NULL,
    cycle_id            BIGINT          REFERENCES cycles(id),
    area_id             BIGINT          REFERENCES areas(id),
    course_id           BIGINT          REFERENCES courses(id),
    created_by          BIGINT          NOT NULL REFERENCES users(id),
    title               VARCHAR(255)    NOT NULL,
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    available_from      TIME,
    available_until     TIME,
    duration_minutes    INTEGER         NOT NULL DEFAULT 60,
    max_attempts        INTEGER         NOT NULL DEFAULT 1,
    passing_score       DECIMAL(5,2)    NOT NULL DEFAULT 50.00,
    is_published        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_exam_scope CHECK (scope_type IN ('CYCLE', 'AREA', 'COURSE')),
    CONSTRAINT chk_exam_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'FINISHED', 'GRADED'))
);

CREATE TABLE questions (
    id                      BIGSERIAL       PRIMARY KEY,
    uuid                    UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id              BIGINT          NOT NULL REFERENCES academies(id),
    exam_id                 BIGINT          NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_text           TEXT            NOT NULL,
    question_type           VARCHAR(30)     NOT NULL,
    options                 JSONB,
    correct_answer          JSONB,
    points                  DECIMAL(5,2)    NOT NULL DEFAULT 1.00,
    order_index             INTEGER         NOT NULL DEFAULT 0,
    requires_manual_grading BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_question_type CHECK (question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TEXT', 'FILE_UPLOAD'))
);

CREATE TABLE exam_attempts (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    exam_id         BIGINT          NOT NULL REFERENCES exams(id),
    student_id      BIGINT          NOT NULL REFERENCES students(id),
    attempt_number  INTEGER         NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    submitted_at    TIMESTAMP,
    answers         JSONB,
    auto_score      DECIMAL(5,2),
    manual_score    DECIMAL(5,2),
    total_score     DECIMAL(5,2),
    passed          BOOLEAN,
    graded_by       BIGINT          REFERENCES users(id),
    graded_at       TIMESTAMP,

    CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED'))
);

CREATE INDEX idx_exams_academy_id ON exams(academy_id);
CREATE INDEX idx_exams_scope ON exams(scope_type, cycle_id, area_id, course_id);
CREATE INDEX idx_exams_status ON exams(academy_id, status);
CREATE INDEX idx_exams_created_by ON exams(created_by);
CREATE INDEX idx_questions_exam_id ON questions(exam_id);
CREATE INDEX idx_questions_academy_id ON questions(academy_id);
CREATE INDEX idx_exam_attempts_academy_id ON exam_attempts(academy_id);
CREATE INDEX idx_exam_attempts_exam_id ON exam_attempts(exam_id);
CREATE INDEX idx_exam_attempts_student_id ON exam_attempts(student_id);
CREATE INDEX idx_exam_attempts_status ON exam_attempts(exam_id, status);
