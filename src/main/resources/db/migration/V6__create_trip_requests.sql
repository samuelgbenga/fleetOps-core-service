CREATE TABLE trip_requests (
    id               BIGSERIAL    PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES companies(id),
    vehicle_id       BIGINT       NOT NULL REFERENCES vehicles(id),
    requested_by_id  BIGINT       NOT NULL REFERENCES users(id),
    destination      VARCHAR(255) NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    approved_at      TIMESTAMP,
    completed_at     TIMESTAMP
);