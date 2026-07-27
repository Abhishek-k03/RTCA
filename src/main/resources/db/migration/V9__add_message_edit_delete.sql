ALTER TABLE messages
    ADD COLUMN edited_at  TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- "delete for me": messages a user has hidden from their own history
CREATE TABLE message_hides (
    user_id    BIGINT NOT NULL REFERENCES users (id),
    message_id BIGINT NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, message_id)
);
