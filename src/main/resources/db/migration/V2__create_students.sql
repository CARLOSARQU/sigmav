-- V2__create_students.sql

CREATE TABLE students (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    user_id     BIGINT          REFERENCES users(id),
    first_name  VARCHAR(150)    NOT NULL,
    last_name   VARCHAR(150)    NOT NULL,
    dni         VARCHAR(20)     NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(30),
    code        VARCHAR(50),
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_students_academy_dni UNIQUE (academy_id, dni),
    CONSTRAINT uq_students_academy_code UNIQUE (academy_id, code)
);

CREATE INDEX idx_students_academy_id ON students(academy_id);
CREATE INDEX idx_students_user_id ON students(user_id);
CREATE INDEX idx_students_dni ON students(academy_id, dni);
