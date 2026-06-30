CREATE TABLE conversations (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(16)  NOT NULL,
    name            VARCHAR(100),
    direct_key      VARCHAR(64),
    created_by      BIGINT       NOT NULL REFERENCES users (id),
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- one direct chat per user pair, enforced by the db
    CONSTRAINT uk_conversations_direct_key UNIQUE (direct_key),
    CONSTRAINT ck_conversations_direct CHECK (
        (type = 'DIRECT' AND direct_key IS NOT NULL) OR (type = 'GROUP' AND direct_key IS NULL)
    )
);

CREATE TABLE conversation_participants (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    role            VARCHAR(16) NOT NULL,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_participants_user ON conversation_participants (user_id);
