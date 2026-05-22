CREATE TABLE vehicle_images (
    id         BIGSERIAL    PRIMARY KEY,
    vehicle_id BIGINT       NOT NULL REFERENCES vehicles(id),
    image_url  TEXT         NOT NULL,
    image_id   VARCHAR(255) NOT NULL
);
