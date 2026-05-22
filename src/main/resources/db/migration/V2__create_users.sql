CREATE TABLE users (
    id                   BIGSERIAL    PRIMARY KEY,
    company_id           BIGINT       REFERENCES companies(id),
    name                 VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(50)  NOT NULL,
    user_type            VARCHAR(50)  NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    average_rating       DOUBLE PRECISION,
    total_jobs_completed INT          DEFAULT 0,
    available            BOOLEAN,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);