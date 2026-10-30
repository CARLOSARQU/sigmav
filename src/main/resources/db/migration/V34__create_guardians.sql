CREATE TABLE guardians (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    academy_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(255),
    relationship VARCHAR(20) NOT NULL DEFAULT 'OTRO',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guardians_student_id ON guardians(student_id);
CREATE INDEX idx_guardians_academy_id ON guardians(academy_id);
CREATE INDEX idx_guardians_email ON guardians(email);

CREATE TABLE sent_report_log (
    id BIGSERIAL PRIMARY KEY,
    guardian_id BIGINT NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(guardian_id, week_start)
);
