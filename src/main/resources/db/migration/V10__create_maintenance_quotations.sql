CREATE TABLE maintenance_quotations (
    id               BIGSERIAL      PRIMARY KEY,
    flag_id          BIGINT         NOT NULL REFERENCES maintenance_flags(id),
    crew_id          BIGINT         NOT NULL REFERENCES users(id),
    company_id       BIGINT         NOT NULL REFERENCES companies(id),
    estimated_cost   DECIMAL(15,2)  NOT NULL,
    description      TEXT           NOT NULL,
    parts_needed     TEXT,
    actual_cost      DECIMAL(15,2),
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    revision_number  INT            NOT NULL DEFAULT 1,
    submitted_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMP
);