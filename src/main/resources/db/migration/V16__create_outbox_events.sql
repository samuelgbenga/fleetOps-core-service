CREATE TABLE outbox_events (
    id           BIGSERIAL    PRIMARY KEY,
    topic        VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    retry_count  INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP
);