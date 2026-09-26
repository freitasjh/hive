CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_agent_prompt_template') THEN
        ALTER TABLE agent ADD CONSTRAINT fk_agent_prompt_template
            FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE RESTRICT;
    ELSE
        IF EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_name='fk_agent_prompt_template' AND delete_rule='SET NULL') THEN
            ALTER TABLE agent DROP CONSTRAINT fk_agent_prompt_template;
            ALTER TABLE agent ADD CONSTRAINT fk_agent_prompt_template
                FOREIGN KEY (prompt_template_id) REFERENCES prompt_template(id) ON DELETE RESTRICT;
        END IF;
    END IF;
EXCEPTION WHEN duplicate_object THEN NULL;
WHEN others THEN
    RAISE WARNING 'fk_agent_prompt_template V8 failed: %', SQLERRM;
    RAISE EXCEPTION 'fk_agent_prompt_template constraint missing after V8: %', SQLERRM;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_agent_prompt_template') THEN
        RAISE EXCEPTION 'V8 verification failed: fk_agent_prompt_template not found after migration';
    ELSE
        RAISE NOTICE 'V8 verification ok: fk_agent_prompt_template present';
    END IF;
END $$;
