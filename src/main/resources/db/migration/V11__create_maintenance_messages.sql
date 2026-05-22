CREATE TABLE maintenance_flag_messages (
    id          BIGSERIAL    PRIMARY KEY,
    flag_id     BIGINT       NOT NULL REFERENCES maintenance_flags(id),
    sender_id   BIGINT       NOT NULL REFERENCES users(id),
    sender_name VARCHAR(255) NOT NULL,
    sender_role VARCHAR(50)  NOT NULL,
    message     TEXT         NOT NULL,
    sent_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);