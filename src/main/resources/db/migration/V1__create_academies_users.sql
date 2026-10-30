-- V1__create_academies_users.sql

CREATE TABLE academies (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name        VARCHAR(255)    NOT NULL,
    subdomain   VARCHAR(100)    NOT NULL UNIQUE,
    plan_type   VARCHAR(50)     NOT NULL DEFAULT 'FREE',
    max_students    INTEGER     NOT NULL DEFAULT 50,
    max_courses     INTEGER     NOT NULL DEFAULT 10,
    max_storage_mb  INTEGER     NOT NULL DEFAULT 500,
    features        JSONB       NOT NULL DEFAULT '{}',
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    subscription_start  TIMESTAMP,
    subscription_end    TIMESTAMP,
    config          JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    uuid            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id      BIGINT          REFERENCES academies(id),
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255)    NOT NULL,
    role            VARCHAR(30)     NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email_academy UNIQUE (email, academy_id),
    CONSTRAINT chk_users_role CHECK (role IN ('SUPER_ADMIN', 'ACADEMY_ADMIN', 'TEACHER', 'STUDENT'))
);

-- Indexes
CREATE INDEX idx_users_academy_id ON users(academy_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_academies_subdomain ON academies(subdomain);
CREATE INDEX idx_academies_is_active ON academies(is_active);
