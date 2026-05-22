CREATE TABLE companies (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(255) NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    contact_phone    VARCHAR(50),
    address          TEXT,
    logo_url         TEXT,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    registered_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    approved_at      TIMESTAMP
);
