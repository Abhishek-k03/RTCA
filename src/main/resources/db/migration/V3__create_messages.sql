CREATE TABLE messages (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id         BIGINT      NOT NULL REFERENCES users (id),
    content           TEXT        NOT NULL,
    type              VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    client_message_id VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_messages_client_id UNIQUE (sender_id, client_message_id)
);

-- history is read newest first by keyset on id
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id, id DESC);
