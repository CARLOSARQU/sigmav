-- V3__create_academy_subscriptions.sql

CREATE TABLE academy_subscriptions (
    id              BIGSERIAL       PRIMARY KEY,
    academy_id      BIGINT          NOT NULL REFERENCES academies(id),
    plan_type       VARCHAR(50)     NOT NULL,
    amount_paid     DECIMAL(10,2)   NOT NULL,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'PEN',
    period_start    TIMESTAMP       NOT NULL,
    period_end      TIMESTAMP       NOT NULL,
    payment_method  VARCHAR(50),
    notes           TEXT,
    created_by      BIGINT          REFERENCES users(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_academy_subs_academy_id ON academy_subscriptions(academy_id);
CREATE INDEX idx_academy_subs_period ON academy_subscriptions(period_start, period_end);
