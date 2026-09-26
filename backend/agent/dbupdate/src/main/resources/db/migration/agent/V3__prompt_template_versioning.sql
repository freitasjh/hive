ALTER TABLE prompt_template ADD COLUMN code VARCHAR(255);
UPDATE prompt_template SET code = name WHERE code IS NULL;
ALTER TABLE prompt_template ALTER COLUMN code SET NOT NULL;

ALTER TABLE prompt_template ADD COLUMN status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL;
ALTER TABLE prompt_template ADD COLUMN params text[];

-- Constraint de unicidade por versão
ALTER TABLE prompt_template ADD CONSTRAINT uk_prompt_template_tenant_code_version UNIQUE (tenant_id, code, version);

-- Índice parcial para garantir apenas uma versão ACTIVE por code por tenant
CREATE UNIQUE INDEX uk_prompt_template_active_version 
ON prompt_template (tenant_id, code) 
WHERE status = 'ACTIVE';
