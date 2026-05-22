CREATE TABLE service_histories (
    id                     BIGSERIAL        PRIMARY KEY,
    vehicle_id             BIGINT           NOT NULL REFERENCES vehicles(id),
    fleet_manager_name     VARCHAR(255)     NOT NULL,
    notes                  TEXT             NOT NULL,
    new_milestone_interval DOUBLE PRECISION NOT NULL,
    actual_cost            DECIMAL(15,2),
    serviced_at            TIMESTAMP        NOT NULL DEFAULT NOW()
);