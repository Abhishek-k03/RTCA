ALTER TABLE messages
    ADD COLUMN reply_to_id BIGINT REFERENCES messages (id);

-- one reaction per member per message, picking another one replaces it
CREATE TABLE message_reactions (
    message_id BIGINT      NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);
