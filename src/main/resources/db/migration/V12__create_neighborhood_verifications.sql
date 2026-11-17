CREATE TABLE brew_market.neighborhoods (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE brew_market.user_neighborhood_verifications (
    user_id BIGINT NOT NULL REFERENCES brew_market.users(id),
    neighborhood_id BIGINT NOT NULL REFERENCES brew_market.neighborhoods(id),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, neighborhood_id)
);
