-- V4__create_cycles_areas.sql

CREATE TABLE cycles (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    name        VARCHAR(255)    NOT NULL,
    start_date  DATE            NOT NULL,
    end_date    DATE            NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE areas (
    id          BIGSERIAL       PRIMARY KEY,
    uuid        UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id  BIGINT          NOT NULL REFERENCES academies(id),
    cycle_id    BIGINT          NOT NULL REFERENCES cycles(id),
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cycles_academy_id ON cycles(academy_id);
CREATE INDEX idx_cycles_active ON cycles(academy_id, is_active);
CREATE INDEX idx_areas_academy_id ON areas(academy_id);
CREATE INDEX idx_areas_cycle_id ON areas(cycle_id);
