CREATE TABLE brew_market.image_deletion_jobs (
    image_key VARCHAR(100) PRIMARY KEY,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
