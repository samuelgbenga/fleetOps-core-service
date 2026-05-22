CREATE TABLE vehicle_activity_logs (
    id           BIGSERIAL    PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES companies(id),
    vehicle_id   BIGINT       NOT NULL,
    plate_number VARCHAR(20)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    description  TEXT         NOT NULL,
    actor_name   VARCHAR(255),
    actor_role   VARCHAR(50),
    occurred_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);