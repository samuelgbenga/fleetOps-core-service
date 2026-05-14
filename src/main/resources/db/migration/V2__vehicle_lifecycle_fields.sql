ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS lifecycle_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN IF NOT EXISTS max_mileage          DOUBLE PRECISION NOT NULL DEFAULT 300000.0,
    ADD COLUMN IF NOT EXISTS max_trips            INTEGER          NOT NULL DEFAULT 500,
    ADD COLUMN IF NOT EXISTS max_maintenance_rounds INTEGER        NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS marked_for_sale      BOOLEAN          NOT NULL DEFAULT FALSE;

ALTER TABLE mileage_logs
    ADD COLUMN IF NOT EXISTS trip_request_id BIGINT;

ALTER TABLE DROP CONSTRAINT IF EXISTS fk_mileage_log_trip_request;

ALTER TABLE mileage_logs
    ADD CONSTRAINT fk_mileage_log_trip_request
        FOREIGN KEY (trip_request_id) REFERENCES trip_requests (id);