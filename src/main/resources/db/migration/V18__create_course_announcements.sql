-- V18: Anuncios por curso (docente → alumnos)
CREATE TABLE course_announcements (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT       NOT NULL,
    course_id   BIGINT       NOT NULL,
    created_by  BIGINT       NOT NULL,  -- users.id del docente o admin
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_announcements_course ON course_announcements (course_id, is_active, created_at DESC);
CREATE INDEX idx_announcements_academy ON course_announcements (academy_id);
