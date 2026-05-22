CREATE TABLE vehicle_assignments (
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT    NOT NULL REFERENCES companies(id),
    trip_request_id  BIGINT    NOT NULL REFERENCES trip_requests(id),
    vehicle_id       BIGINT    NOT NULL REFERENCES vehicles(id),
    assigned_to_id   BIGINT    NOT NULL REFERENCES users(id),
    assigned_at      TIMESTAMP NOT NULL DEFAULT NOW()
);