CREATE TABLE breakdown_reports (
    id                      BIGSERIAL        PRIMARY KEY,
    company_id              BIGINT           NOT NULL REFERENCES companies(id),
    vehicle_id              BIGINT           NOT NULL REFERENCES vehicles(id),
    trip_request_id         BIGINT           REFERENCES trip_requests(id),
    field_staff_id          BIGINT           NOT NULL REFERENCES users(id),
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    location_description    TEXT,
    description             TEXT             NOT NULL,
    status                  VARCHAR(50)      NOT NULL DEFAULT 'REPORTED',
    assigned_crew_id        BIGINT           REFERENCES users(id),
    replacement_vehicle_id  BIGINT           REFERENCES vehicles(id),
    maintenance_flag_id     BIGINT           REFERENCES maintenance_flags(id),
    reported_at             TIMESTAMP        NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMP
);