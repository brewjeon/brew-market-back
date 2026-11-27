ALTER TABLE brew_market.inquiry_messages
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'TEXT',
    ADD CONSTRAINT inquiry_messages_kind_check CHECK (kind IN ('TEXT', 'SYSTEM'));
