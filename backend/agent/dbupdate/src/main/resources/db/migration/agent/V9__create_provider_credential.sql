CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- BE-02 (DDL): tabela de credenciais de provider por tenant.
-- BE-07 (dados, Batch 2) usará a V10: UPDATE de agent por llm_model.
-- Decisão: DDL e dados em migrations separadas (V9 estrutura, V10 dados).

CREATE TABLE IF NOT EXISTS provider_credential
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID NOT NULL,
    provider    VARCHAR(32) NOT NULL,
    ciphertext  TEXT,
    key_version INT NOT NULL DEFAULT 1,
    base_url    VARCHAR(2048),

    CONSTRAINT pk_provider_credential PRIMARY KEY (id)
);

DO $$
BEGIN
    -- Sem FK cross-module p/ tenant(id): histories Flyway são independentes e sem
    -- ordem garantida (mesmo padrão da tabela agent, que usa tenant_id sem FK).
    -- Isolamento e cascade de tenant são responsabilidade da camada de aplicação.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uk_provider_credential_tenant_provider') THEN
        ALTER TABLE provider_credential ADD CONSTRAINT uk_provider_credential_tenant_provider
            UNIQUE (tenant_id, provider);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_provider_credential_tenant
    ON provider_credential(tenant_id);
