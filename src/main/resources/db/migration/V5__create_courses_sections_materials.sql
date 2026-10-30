-- V5__create_courses_sections_materials.sql

CREATE TABLE courses (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    area_id     BIGINT          NOT NULL REFERENCES areas(id),
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE course_sections (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    course_id   BIGINT          NOT NULL REFERENCES courses(id),
    title       VARCHAR(255)    NOT NULL,
    order_index INTEGER         NOT NULL DEFAULT 0,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE course_materials (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    course_id       BIGINT          NOT NULL REFERENCES courses(id),
    section_id      BIGINT          REFERENCES course_sections(id),
    title           VARCHAR(255)    NOT NULL,
    material_type   VARCHAR(50)     NOT NULL,
    file_url        TEXT,
    order_index     INTEGER         NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_material_type CHECK (material_type IN ('PDF_DRIVE', 'PDF_S3', 'LINK', 'VIDEO'))
);

CREATE INDEX idx_courses_academy_id ON courses(academy_id);
CREATE INDEX idx_courses_area_id ON courses(area_id);
CREATE INDEX idx_course_sections_course_id ON course_sections(course_id);
CREATE INDEX idx_course_materials_course_id ON course_materials(course_id);
CREATE INDEX idx_course_materials_section_id ON course_materials(section_id);
