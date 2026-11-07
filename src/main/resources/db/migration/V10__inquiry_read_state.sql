ALTER TABLE brew_market.inquiry_rooms
    ADD COLUMN buyer_last_read_id BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN seller_last_read_id BIGINT NOT NULL DEFAULT 0;
