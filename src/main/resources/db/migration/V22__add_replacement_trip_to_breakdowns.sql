ALTER TABLE breakdown_reports
    ADD COLUMN replacement_trip_request_id BIGINT REFERENCES trip_requests(id);
