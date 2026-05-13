ALTER TABLE vehicles
    ADD COLUMN lifecycle_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN max_mileage          DOUBLE PRECISION NOT NULL DEFAULT 300000.0,
    ADD COLUMN max_trips            INTEGER          NOT NULL DEFAULT 500,
    ADD COLUMN max_maintenance_rounds INTEGER        NOT NULL DEFAULT 30,
    ADD COLUMN marked_for_sale      BOOLEAN          NOT NULL DEFAULT FALSE;

ALTER TABLE mileage_logs
    ADD COLUMN trip_request_id BIGINT,
    ADD CONSTRAINT fk_mileage_log_trip_request
        FOREIGN KEY (trip_request_id) REFERENCES trip_requests (id);
