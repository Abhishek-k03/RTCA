ALTER TABLE conversations
    ADD COLUMN avatar_id UUID REFERENCES files (id) ON DELETE SET NULL;
