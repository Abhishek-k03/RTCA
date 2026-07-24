-- removed members keep read-only access to history up to removed_after_message_id
ALTER TABLE conversation_participants
    ADD COLUMN removed_at               TIMESTAMPTZ,
    ADD COLUMN removed_after_message_id BIGINT;
