-- uploaded images: profile pictures and chat photos. the id is also the storage key
CREATE TABLE files (
    id           UUID PRIMARY KEY,
    owner_id     BIGINT      NOT NULL REFERENCES users (id),
    purpose      VARCHAR(16) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    width        INT,
    height       INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users
    ADD COLUMN bio VARCHAR(200),
    ADD COLUMN avatar_id UUID REFERENCES files (id) ON DELETE SET NULL;

ALTER TABLE messages
    ADD COLUMN file_id UUID REFERENCES files (id) ON DELETE SET NULL;

-- file access checks look up the message by its file
CREATE INDEX idx_messages_file_id ON messages (file_id) WHERE file_id IS NOT NULL;
