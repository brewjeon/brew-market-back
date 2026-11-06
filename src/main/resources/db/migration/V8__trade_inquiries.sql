CREATE TABLE brew_market.inquiry_rooms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES brew_market.posts(id),
    buyer_id BIGINT NOT NULL REFERENCES brew_market.users(id),
    seller_id BIGINT NOT NULL REFERENCES brew_market.users(id),
    UNIQUE (post_id, buyer_id),
    CHECK (buyer_id <> seller_id)
);

CREATE TABLE brew_market.inquiry_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES brew_market.inquiry_rooms(id),
    sender_id BIGINT NOT NULL REFERENCES brew_market.users(id),
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000)
);

CREATE INDEX inquiry_messages_room_id_idx ON brew_market.inquiry_messages (room_id, id DESC);
CREATE INDEX inquiry_rooms_buyer_idx ON brew_market.inquiry_rooms (buyer_id);
CREATE INDEX inquiry_rooms_seller_idx ON brew_market.inquiry_rooms (seller_id);
