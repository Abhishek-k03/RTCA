-- random ids for everything exposed in urls, apis and websocket topics.
-- the numeric ids stay internal
ALTER TABLE users
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE users
    ADD CONSTRAINT uk_users_public_id UNIQUE (public_id);

ALTER TABLE conversations
    ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE conversations
    ADD CONSTRAINT uk_conversations_public_id UNIQUE (public_id);
