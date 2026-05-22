CREATE TABLE media (
    id         BIGSERIAL    PRIMARY KEY,
    public_id  VARCHAR(255) NOT NULL UNIQUE,
    url        TEXT         NOT NULL,
    owner_type VARCHAR(50)  NOT NULL,
    owner_id   BIGINT       NOT NULL
);