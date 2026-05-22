CREATE TABLE maintenance_ratings (
    id               BIGSERIAL  PRIMARY KEY,
    flag_id          BIGINT     NOT NULL REFERENCES maintenance_flags(id),
    crew_id          BIGINT     NOT NULL REFERENCES users(id),
    company_id       BIGINT     NOT NULL REFERENCES companies(id),
    rated_by_user_id BIGINT     NOT NULL REFERENCES users(id),
    stars            INT        NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment          TEXT,
    rated_at         TIMESTAMP  NOT NULL DEFAULT NOW()
);