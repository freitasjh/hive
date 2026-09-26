CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- prompt_template: description TEXT, tags TEXT[], inference_params JSONB, template TEXT
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='prompt_template' AND column_name='description') THEN
        ALTER TABLE prompt_template ADD COLUMN description TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='prompt_template' AND column_name='tags') THEN
        ALTER TABLE prompt_template ADD COLUMN tags TEXT[];
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='prompt_template' AND column_name='inference_params') THEN
        ALTER TABLE prompt_template ADD COLUMN inference_params JSONB;
    END IF;
END $$;

-- template VARCHAR(8000) -> TEXT (sem perda)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='prompt_template' AND column_name='template' AND data_type='character varying'
    ) THEN
        ALTER TABLE prompt_template ALTER COLUMN template TYPE TEXT;
    END IF;
END $$;

-- prompt_template: garantir tenant_id NOT NULL (se V3 deixou nullable)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM prompt_template WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'tenant_id null found in prompt_template, manual migration required for tenant=default (lookup SELECT id FROM tenant WHERE code=''default'')';
    END IF;
    BEGIN
        ALTER TABLE prompt_template ALTER COLUMN tenant_id SET NOT NULL;
    EXCEPTION WHEN others THEN
        RAISE WARNING 'prompt_template tenant_id still has nulls, skipping SET NOT NULL';
    END;
END $$;

-- Unique constraint for versioning already exists from V3; ensure partial unique for ACTIVE code per tenant
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uniq_prompt_template_tenant_code_version') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname LIKE '%tenant%code%version%') THEN
            BEGIN
                ALTER TABLE prompt_template ADD CONSTRAINT uniq_prompt_template_tenant_code_version UNIQUE (tenant_id, code, version);
            EXCEPTION WHEN duplicate_table THEN NULL;
                WHEN duplicate_object THEN NULL;
            END;
        END IF;
    END IF;
EXCEPTION WHEN duplicate_table THEN NULL;
END $$;

CREATE UNIQUE INDEX uniq_prompt_active_code_per_tenant
    ON prompt_template(tenant_id, code) WHERE status = 'ACTIVE';

CREATE INDEX idx_prompt_template_tenant_code ON prompt_template(tenant_id, code);
CREATE INDEX idx_prompt_template_tenant_status ON prompt_template(tenant_id, status);
CREATE INDEX idx_prompt_template_tenant_create_at ON prompt_template(tenant_id, create_at DESC);

-- agent: avatar_url, system_prompt, top_p, max_tokens, tools, knowledge_bases (idempotente, complemento para BE-02/BE-08)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='avatar_url') THEN
        ALTER TABLE agent ADD COLUMN avatar_url VARCHAR(2048);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='system_prompt') THEN
        ALTER TABLE agent ADD COLUMN system_prompt TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='top_p') THEN
        ALTER TABLE agent ADD COLUMN top_p DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='max_tokens') THEN
        ALTER TABLE agent ADD COLUMN max_tokens INT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='tools') THEN
        ALTER TABLE agent ADD COLUMN tools TEXT[];
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agent' AND column_name='knowledge_bases') THEN
        ALTER TABLE agent ADD COLUMN knowledge_bases TEXT[];
    END IF;
END $$;

-- Garantir FK com ON DELETE RESTRICT idempotente
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_agent_prompt_template') THEN
        ALTER TABLE agent ADD CONSTRAINT fk_agent_prompt_template
            FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE RESTRICT;
    END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_agent_prompt_template') THEN
        IF EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_name='fk_agent_prompt_template' AND delete_rule='SET NULL') THEN
            ALTER TABLE agent DROP CONSTRAINT fk_agent_prompt_template;
            ALTER TABLE agent ADD CONSTRAINT fk_agent_prompt_template
                FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE RESTRICT;
        END IF;
    END IF;
EXCEPTION WHEN others THEN
    RAISE WARNING 'fk_agent_prompt_template correction skipped: %', SQLERRM;
END $$;
