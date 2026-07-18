ALTER TABLE conversation_participants
    ADD COLUMN last_delivered_message_id BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_read_message_id      BIGINT NOT NULL DEFAULT 0;
