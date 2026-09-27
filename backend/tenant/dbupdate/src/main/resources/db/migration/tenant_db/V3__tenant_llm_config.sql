-- Assistant (decisão de produto 2026-09-27): o provedor/modelo padrão é do TENANT e existe um só.
--
-- Não há fallback global nem criação automática. Sem esta linha o chat falha com
-- LLM_CONFIG_NOT_FOUND (422) dizendo exatamente o que configurar — um default implícito
-- esconderia a configuração ausente atrás de um comportamento que "funciona" e não é diagnosticável.
--
-- FK para tenant(id) é segura AQUI e só aqui: esta tabela e `tenant` vivem no MESMO histórico
-- Flyway (flyway_schema_history_tenant), logo a ordem de criação é garantida. O módulo `agent`
-- deliberadamente NÃO usa FK cross-module (mesmo comentário na V9 dele): historiales independentes
-- e sem ordem garantida. Esta é a razão pela qual a configuração de LLM mora no módulo `tenant`.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS tenant_llm_config
(
    id        UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at TIMESTAMP WITHOUT TIME ZONE,
    update_at TIMESTAMP WITHOUT TIME ZONE,
    tenant_id UUID NOT NULL,
    provider  VARCHAR(32)  NOT NULL,
    model     VARCHAR(255) NOT NULL,

    CONSTRAINT pk_tenant_llm_config PRIMARY KEY (id),
    CONSTRAINT uk_tenant_llm_config_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_llm_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),

    -- Allowlist explícita, e não negação. Um provider novo entra aqui por decisão explícita,
    -- nunca por omissão — mesma política de PROVIDERS_WITH_TENANT_BASE_URL no frontend. O CHECK
    -- é a última linha de defesa: a allowlist do Java é a primeira.
    CONSTRAINT chk_tenant_llm_config_provider CHECK (provider IN
        ('OPENAI', 'GEMINI', 'ANTHROPIC', 'OLLAMA', 'OPENAI_COMPATIBLE', 'OPENCODE_GO', 'OPENCODE_ZEN')),

    -- `model` não pode ser vazio: um model em branco é indistinguível de "não configurado" e
    -- só apareceria como erro do provider, muito depois de onde a falha foi cometida.
    CONSTRAINT chk_tenant_llm_config_model CHECK (length(btrim(model)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_tenant_llm_config_tenant ON tenant_llm_config (tenant_id);
