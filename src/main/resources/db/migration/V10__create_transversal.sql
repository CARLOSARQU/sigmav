-- V10__create_transversal.sql

CREATE TABLE active_sessions (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL UNIQUE REFERENCES users(id),
    session_token   VARCHAR(255)    NOT NULL UNIQUE,
    device_info     JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id          BIGSERIAL       PRIMARY KEY,
    target_type VARCHAR(20)     NOT NULL,
    academy_id  BIGINT          REFERENCES academies(id),
    user_id     BIGINT          REFERENCES users(id),
    type        VARCHAR(50)     NOT NULL,
    title       VARCHAR(255)    NOT NULL,
    message     TEXT            NOT NULL,
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notification_target CHECK (target_type IN ('ACADEMY', 'USER'))
);

CREATE TABLE student_progress (
    id          BIGSERIAL       PRIMARY KEY,
    student_id  BIGINT          NOT NULL REFERENCES students(id),
    material_id BIGINT          NOT NULL REFERENCES course_materials(id),
    viewed_at   TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_material UNIQUE (student_id, material_id)
);

CREATE INDEX idx_active_sessions_user_id ON active_sessions(user_id);
CREATE INDEX idx_active_sessions_token ON active_sessions(session_token);
CREATE INDEX idx_notifications_academy_id ON notifications(academy_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read);
CREATE INDEX idx_student_progress_student ON student_progress(student_id);
CREATE INDEX idx_student_progress_material ON student_progress(material_id);
