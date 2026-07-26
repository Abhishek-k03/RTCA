-- "clear chat": this member no longer sees messages up to this id
ALTER TABLE conversation_participants
    ADD COLUMN cleared_up_to_message_id BIGINT NOT NULL DEFAULT 0;
