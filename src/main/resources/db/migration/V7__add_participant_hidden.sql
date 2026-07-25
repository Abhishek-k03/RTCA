-- a new direct chat stays out of the receiver's list until the first message
ALTER TABLE conversation_participants
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
