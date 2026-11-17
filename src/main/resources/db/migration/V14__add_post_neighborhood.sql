ALTER TABLE brew_market.posts
    ADD COLUMN neighborhood_id BIGINT REFERENCES brew_market.neighborhoods(id);
