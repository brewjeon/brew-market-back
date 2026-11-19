ALTER TABLE brew_market.posts
    ADD COLUMN reservation_room_id BIGINT REFERENCES brew_market.inquiry_rooms(id),
    ADD CONSTRAINT posts_reservation_status_check
        CHECK (reservation_room_id IS NULL OR status IN ('RESERVED', 'COMPLETED'));
