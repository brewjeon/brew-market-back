CREATE TABLE brew_market.post_favorites (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES brew_market.users(id),
    post_id BIGINT NOT NULL REFERENCES brew_market.posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT post_favorites_user_post_unique UNIQUE (user_id, post_id)
);

CREATE INDEX post_favorites_user_recent_idx ON brew_market.post_favorites (user_id, id DESC);
