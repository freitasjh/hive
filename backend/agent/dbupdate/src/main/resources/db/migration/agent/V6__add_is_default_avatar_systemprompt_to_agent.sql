CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='agent' AND column_name='is_default'
    ) THEN
        ALTER TABLE agent ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='avatar_url') THEN
        ALTER TABLE agent ADD COLUMN avatar_url VARCHAR(2048);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='system_prompt') THEN
        ALTER TABLE agent ADD COLUMN system_prompt TEXT;
    END IF;
END $$;

CREATE UNIQUE INDEX uniq_agent_default_per_tenant
    ON agent(tenant_id) WHERE is_default = true;

CREATE INDEX idx_agent_tenant_update_at
    ON agent(tenant_id, update_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM agent WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'tenant_id null found in agent, manual migration required for tenant=default (lookup SELECT id FROM tenant WHERE code=''default'')';
    END IF;
END $$;
