ALTER TABLE brew_market.posts
    ADD COLUMN meeting_at TIMESTAMPTZ,
    ADD COLUMN meeting_place VARCHAR(200),
    ADD CONSTRAINT posts_meeting_details_check CHECK (
        (meeting_at IS NULL AND meeting_place IS NULL)
        OR (
            meeting_at IS NOT NULL
            AND meeting_place IS NOT NULL
            AND char_length(btrim(meeting_place)) > 0
            AND reservation_room_id IS NOT NULL
            AND status = 'RESERVED'
        )
    );
