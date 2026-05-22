CREATE TABLE mileage_logs (
    id               BIGSERIAL        PRIMARY KEY,
    company_id       BIGINT           NOT NULL REFERENCES companies(id),
    vehicle_id       BIGINT           NOT NULL REFERENCES vehicles(id),
    submitted_by_id  BIGINT           NOT NULL REFERENCES users(id),
    trip_request_id  BIGINT           REFERENCES trip_requests(id),
    reported_mileage DOUBLE PRECISION NOT NULL,
    logged_at        TIMESTAMP        NOT NULL DEFAULT NOW()
);