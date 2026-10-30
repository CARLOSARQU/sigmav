-- V29__create_simulacro_tables.sql
-- Creates simulacro hierarchy tables (university -> area -> career)
-- and the exam-career join table.

CREATE TABLE simulacro_universities (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_simulacro_university UNIQUE (name, academy_id)
);

CREATE TABLE simulacro_areas (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    university_id   BIGINT          NOT NULL REFERENCES simulacro_universities(id),
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_simulacro_area UNIQUE (name, university_id)
);

CREATE TABLE simulacro_careers (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    area_id     BIGINT          NOT NULL REFERENCES simulacro_areas(id),
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_simulacro_career UNIQUE (name, area_id)
);

CREATE TABLE exam_career_mapping (
    exam_id   BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    career_id BIGINT NOT NULL REFERENCES simulacro_careers(id),
    PRIMARY KEY (exam_id, career_id)
);

-- Tenant-scoped indexes
CREATE INDEX idx_smu_academy_id ON simulacro_universities(academy_id);
CREATE INDEX idx_sma_academy_id ON simulacro_areas(academy_id, university_id);
CREATE INDEX idx_smc_academy_id ON simulacro_careers(academy_id, area_id);
CREATE INDEX idx_ecm_career_id  ON exam_career_mapping(career_id);
CREATE INDEX idx_ecm_exam_id    ON exam_career_mapping(exam_id);
