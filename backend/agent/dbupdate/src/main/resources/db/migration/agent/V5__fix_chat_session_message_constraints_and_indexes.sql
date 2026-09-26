CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Normalize existing titles before constraining
UPDATE chat_session SET title = LEFT(title, 50) WHERE LENGTH(title) > 50;
UPDATE chat_session SET title = 'Nova conversa' WHERE title IS NULL;

-- Normalize legacy role values to uppercase before constraining (idempotent)
UPDATE chat_message SET role = UPPER(role) WHERE role IS NOT NULL AND role IN ('user','assistant');

-- Ensure tenant_id not null (fill with random if null to avoid failure in dev with dirty data)
UPDATE chat_session SET tenant_id = uuid_generate_v4() WHERE tenant_id IS NULL;
UPDATE chat_message SET tenant_id = uuid_generate_v4() WHERE tenant_id IS NULL;

-- Fix title column: 255 -> 50, NOT NULL
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='chat_session' AND column_name='title' AND character_maximum_length <> 50) THEN
        ALTER TABLE chat_session ALTER COLUMN title TYPE VARCHAR(50) USING LEFT(title, 50);
    END IF;
END $$;

ALTER TABLE chat_session ALTER COLUMN title SET NOT NULL;

-- Tenant not null
ALTER TABLE chat_session ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE chat_message ALTER COLUMN tenant_id SET NOT NULL;

-- Fix chat_message.content: VARCHAR(8000) -> TEXT NOT NULL (SPEC requires TEXT, LLM response >8000 breaks)
UPDATE chat_message SET content = '' WHERE content IS NULL;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='chat_message' AND column_name='content' AND data_type <> 'text') THEN
        ALTER TABLE chat_message ALTER COLUMN content TYPE TEXT;
    END IF;
END $$;
ALTER TABLE chat_message ALTER COLUMN content SET NOT NULL;

-- Ensure agent_id NOT NULL (SPEC RF1) — only apply if no dirty data; otherwise warn
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM chat_session WHERE agent_id IS NULL) THEN
        RAISE WARNING 'chat_session has NULL agent_id rows, skipping SET NOT NULL on agent_id';
    ELSE
        ALTER TABLE chat_session ALTER COLUMN agent_id SET NOT NULL;
    END IF;
END $$;

-- Recreate FK chat_message -> chat_session with ON DELETE CASCADE idempotently
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS fk_chat_message_session;
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_session
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE;

-- FK chat_session.agent_id -> agent(id) if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_chat_session_agent') THEN
        ALTER TABLE chat_session ADD CONSTRAINT fk_chat_session_agent FOREIGN KEY (agent_id) REFERENCES agent(id);
    END IF;
END $$;

-- Indexes for pagination and isolation
CREATE INDEX idx_chat_message_session_id ON chat_message(session_id);
CREATE INDEX idx_chat_message_tenant_session ON chat_message(tenant_id, session_id);
CREATE INDEX idx_chat_session_tenant_user ON chat_session(tenant_id, user_id);
CREATE INDEX idx_chat_session_agent ON chat_session(agent_id);
CREATE INDEX idx_chat_session_update_at ON chat_session(update_at DESC);
CREATE INDEX idx_chat_session_create_at ON chat_session(create_at DESC);
CREATE INDEX idx_chat_message_create_at ON chat_message(create_at);

-- Check constraint for role (uppercase to match ChatRole enum USER/ASSISTANT) — idempotent recreate
ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS chk_chat_message_role;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_chat_message_role') THEN
        ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_role CHECK (role IN ('USER','ASSISTANT'));
    END IF;
END $$;
