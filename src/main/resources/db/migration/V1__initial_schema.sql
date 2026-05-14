-- V1: Base schema for FleetOps Core Service
-- Lifecycle/sale columns added in V2; trip_request_id FK on mileage_logs added in V2.

CREATE TABLE media (
    id        BIGSERIAL    PRIMARY KEY,
    public_id VARCHAR(255) NOT NULL UNIQUE,
    url       VARCHAR(255) NOT NULL
);

CREATE TABLE lga_codes (
    id    BIGSERIAL   PRIMARY KEY,
    code  VARCHAR(3)  NOT NULL UNIQUE,
    lga   VARCHAR(255) NOT NULL,
    state VARCHAR(255) NOT NULL
);

CREATE TABLE users (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    role             VARCHAR(50)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    profile_media_id BIGINT       REFERENCES media(id),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicles (
    id                 BIGSERIAL        PRIMARY KEY,
    make               VARCHAR(255)     NOT NULL,
    model              VARCHAR(255)     NOT NULL,
    plate_number       VARCHAR(255)     NOT NULL UNIQUE,
    current_mileage    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    milestone_interval DOUBLE PRECISION NOT NULL DEFAULT 3000.0,
    status             VARCHAR(50)      NOT NULL DEFAULT 'AVAILABLE',
    registered_at      TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicle_media (
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id),
    media_id   BIGINT NOT NULL UNIQUE REFERENCES media(id),
    PRIMARY KEY (vehicle_id, media_id)
);

CREATE TABLE trip_requests (
    id             BIGSERIAL    PRIMARY KEY,
    field_staff_id BIGINT       NOT NULL REFERENCES users(id),
    vehicle_id     BIGINT       NOT NULL REFERENCES vehicles(id),
    destination    VARCHAR(255) NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicle_assignments (
    id              BIGSERIAL PRIMARY KEY,
    trip_request_id BIGINT    NOT NULL UNIQUE REFERENCES trip_requests(id),
    vehicle_id      BIGINT    NOT NULL REFERENCES vehicles(id),
    start_date      DATE      NOT NULL,
    end_date        DATE      NOT NULL,
    assigned_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE mileage_logs (
    id               BIGSERIAL        PRIMARY KEY,
    vehicle_id       BIGINT           NOT NULL REFERENCES vehicles(id),
    submitted_by     BIGINT           NOT NULL REFERENCES users(id),
    reported_mileage DOUBLE PRECISION NOT NULL,
    logged_at        TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE maintenance_flags (
    id                  BIGSERIAL        PRIMARY KEY,
    vehicle_id          BIGINT           NOT NULL REFERENCES vehicles(id),
    assigned_to         BIGINT           REFERENCES users(id),
    assigned_by         BIGINT           REFERENCES users(id),
    mileage_at_trigger  DOUBLE PRECISION NOT NULL,
    status              VARCHAR(50)      NOT NULL DEFAULT 'OPEN',
    progress_notes      TEXT,
    triggered_at        TIMESTAMP        NOT NULL DEFAULT NOW(),
    assigned_at         TIMESTAMP,
    pending_approval_at TIMESTAMP,
    resolved_at         TIMESTAMP
);

CREATE TABLE maintenance_messages (
    id        BIGSERIAL PRIMARY KEY,
    flag_id   BIGINT    NOT NULL REFERENCES maintenance_flags(id),
    sender_id BIGINT    NOT NULL REFERENCES users(id),
    message   TEXT      NOT NULL,
    sent_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE service_histories (
    id                     BIGSERIAL        PRIMARY KEY,
    vehicle_id             BIGINT           NOT NULL REFERENCES vehicles(id),
    fleet_manager_name     VARCHAR(255)     NOT NULL,
    notes                  TEXT             NOT NULL,
    new_milestone_interval DOUBLE PRECISION NOT NULL,
    serviced_at            TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicle_activity_logs (
    id           BIGSERIAL    PRIMARY KEY,
    vehicle_id   BIGINT       NOT NULL,
    plate_number VARCHAR(10)  NOT NULL,
    event_type   VARCHAR(40)  NOT NULL,
    description  TEXT         NOT NULL,
    actor_name   VARCHAR(255) NOT NULL,
    actor_role   VARCHAR(30)  NOT NULL,
    occurred_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_activity_plate       ON vehicle_activity_logs(plate_number);
CREATE INDEX idx_activity_occurred_at ON vehicle_activity_logs(occurred_at);
