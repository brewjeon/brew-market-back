ALTER TABLE brew_market.inquiry_rooms ADD COLUMN post_title VARCHAR(100);

UPDATE brew_market.inquiry_rooms r
SET post_title = p.title
FROM brew_market.posts p
WHERE p.id = r.post_id;

ALTER TABLE brew_market.inquiry_rooms
    ALTER COLUMN post_title SET NOT NULL,
    ALTER COLUMN post_id DROP NOT NULL,
    DROP CONSTRAINT inquiry_rooms_post_id_fkey,
    ADD CONSTRAINT inquiry_rooms_post_id_fkey
        FOREIGN KEY (post_id) REFERENCES brew_market.posts(id) ON DELETE SET NULL;
