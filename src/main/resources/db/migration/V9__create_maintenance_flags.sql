CREATE TABLE maintenance_flags (
    id                    BIGSERIAL  PRIMARY KEY,
    company_id            BIGINT     NOT NULL REFERENCES companies(id),
    vehicle_id            BIGINT     NOT NULL REFERENCES vehicles(id),
    assigned_crew_id      BIGINT     REFERENCES users(id),
    requested_by_user_id  BIGINT     REFERENCES users(id),
    trigger_type          VARCHAR(50) NOT NULL,
    status                VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    description           TEXT,
    progress_notes        TEXT,
    opened_at             TIMESTAMP  NOT NULL DEFAULT NOW(),
    assigned_at           TIMESTAMP,
    resolved_at           TIMESTAMP
);