-- ============================================================================
-- V3 — provider_credential, no formato final.
--
-- Histórico reescrito em 2026-09-27. A tabela nasce aqui completa. A antiga
-- V10__migrate_provider_to_vendor morreu junto: ela só existia para reescrever
-- `agent.provider_type`/`agent.llm_model`, e `agent` não existe mais. Com a
-- tabela `agent` fora, não há dado legado para migrar — `provider` já guarda o
-- vendor real desde o início, que é o formato final.
--
-- ───────────────────────────────────────────────────────────────────────────────
-- DECISÃO DE DONO DA TABELA — leia antes de "corrigir" o nome
-- ───────────────────────────────────────────────────────────────────────────────
-- O nome `provider_credential` sugere o módulo `provider`. NÃO move. O dono é
-- o módulo `agent`, por três fatos verificados em 2026-09-27:
--
--   1. A entidade mora em `agent/impl/entity/ProviderCredentialEntity.java`, e
--      o serviço, repositório, mapper, controller e criptografia
--      (AesGcmAttributeConverter, CredentialResolver, ProviderMasterKey) estão
--      todos no módulo `agent`. Nenhum deles existe no módulo `provider`.
--   2. O módulo `provider` NÃO TEM `dbupdate` — só `api` e `langchain4j`. Ele
--      não tem como ser dono de tabela: onde a migration morasse, a tabela
--      nasceria no histórico de outro módulo, e os dois histories Flyway são
--      independentes e sem ordem garantida entre si.
--   3. A referência é de mão única: `provider_credential` é citada só por
--      `agent/*`, por `commons/api-tools` (handler de exceção) e por
--      `integration-test`. O módulo `provider` não a cita uma vez sequer.
--
-- O nome é descritivo, não de módulo: é a credencial *de um* provider, não a
-- tabela do módulo provider. `tenant_llm_config` (módulo `tenant`) é a que
-- guarda a configuração de LLM do tenant; esta guarda o segredo. Migrar esta
-- tabela para `provider` significaria dar `dbupdate` ao módulo `provider` e
-- mover criptografia, service e controller para lá — batch próprio, com
-- decisão de arquitetura, não um ajuste de nome.
--
-- Sem FK cross-module para `tenant(id)`: mesma razão documentada no histórico
-- anterior — histories Flyway independentes não garantem ordem, e o isolamento
-- por tenant é responsabilidade da camada de aplicação.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE provider_credential
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID NOT NULL,
    provider    VARCHAR(32) NOT NULL,
    ciphertext  TEXT,
    key_version INT NOT NULL DEFAULT 1,
    base_url    VARCHAR(2048),

    CONSTRAINT pk_provider_credential PRIMARY KEY (id),
    -- Uma credencial por provider, por tenant. `provider` já é o vendor real
    -- ('OPENAI', 'GEMINI', 'OLLAMA', 'OPENAI_COMPATIBLE'), não o framework.
    CONSTRAINT uk_provider_credential_tenant_provider UNIQUE (tenant_id, provider)
);

CREATE INDEX idx_provider_credential_tenant
    ON provider_credential (tenant_id);
