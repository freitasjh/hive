-- ============================================================================
-- V2 — prompt_template, no formato final.
--
-- Histórico reescrito em 2026-09-27 (decisão do usuário: "ajustar as migrations
-- para refletir o que for de criação e configuração, não precisa ter migration
-- para arrumar nenhuma tabela"). O schema nasce aqui completo — nada abaixo
-- corrige, completa ou conserta esta tabela.
--
-- O que esta migration ERA, no histórico anterior, e o que foi absorvido aqui:
--   · V2__create_agent_tables        criava a tabela sem code/status/params
--   · V3__prompt_template_versioning  Adding code/status/params + 2 constraints
--   · V7__extend_prompt_template      Adding description/tags/inference_params
--                                      e TEM TEXT + tenant_id NOT NULL
-- Todas as três viraram uma só.
--
-- DECISÃO — a coluna `version` FICA, apesar do ADR de versionamento
-- ───────────────────────────────────────────────────────────────────────────────
-- `docs/adr/ADR_Prompt_Versioning.md` está `Proposed` e anotado como "não
-- implementada". Isso é verdade do MODELO do ADR (níveis, histórico de
-- transições, rollback) e FALSO do que o schema e o código fazem hoje. O que
-- existe é versionamento inteiro real, em uso:
--   · PromptTemplateEntity.version (`nullable = false`)
--   · PromptTemplateServiceImpl:50-51 → create com version = 1
--   · PromptTemplateServiceImpl:82    → update incrementa version
--   · PromptTemplateServiceImpl:105   → duplicar cria latest.version + 1
--   · PromptTemplateRepository:27,31,35 → ORDER BY version DESC
--   · PromptTemplateRepository:33     → findByTenantIdAndCodeAndVersion
-- Remover `version` derrubaria a busca de "última versão" e a criação de
-- versão incrementada — que é o que a UI de duplicar prompt usa. A constraint
-- UNIQUE (tenant_id, code, version) é o que impede duas versões com o mesmo
-- número, e o índice parcial garante uma única versão ACTIVE por code.
--
-- ───────────────────────────────────────────────────────────────────────────────
-- `version` É NOT NULL, E ISSO NÃO É COSMÉTICO (corrigido 2026-09-27)
-- ───────────────────────────────────────────────────────────────────────────────
-- A primeira versão deste arquivo declarava `version INT DEFAULT 1` — nullable. Com ela, o
-- UNIQUE (tenant_id, code, version) era INERTE nos NULLs: o PostgreSQL trata NULL como distinto
-- em UNIQUE (`NULLS DISTINCT` é o default desde o 15), então duas linhas com o mesmo code,
-- mesmo tenant e version = NULL entravam as duas. Medido no banco, não deduzido:
--
--   INSERT (code='dup', version=NULL)  ->  INSERT 0 1
--   INSERT (code='dup', version=NULL)  ->  INSERT 0 1     <-- as DUAS aceitas
--
-- E o efeito era pior que a constraint inerte, porque `ORDER BY version DESC` é NULLS FIRST no
-- PostgreSQL. `PromptTemplateRepository:27`
-- (`findFirstByCodeAndTenantIdOrderByVersionDesc`) devolvia a linha NULL como se fosse a
-- "última versão", e `PromptTemplateServiceImpl:105` (`latest.getVersion() + 1`) desempacotava
-- Integer — NPE, sem mensagem que dissesse a causa. O mesmo vale para
-- `PromptTemplateServiceImpl:82`, que lê a versão do banco e não da requisição.
--
-- Hoje os 3 caminhos de escrita sempre gravam versão não-nula, então isso não é alcançável pelo
-- serviço — mas o banco continua ACEITANDO a linha, e o próximo batch que abrir um caminho de
-- escrita (import, seed, migration de dado) reintroduz um NPE de "latest version" errada sem
-- deixar rastro. Isso é a mesma família da armadilha do `max(version)` do Flyway, já registrada
-- neste projeto: um valor que parece numérico e compara como texto, ou uma coluna que parece
-- obrigatória e aceita NULL.
--
-- `chk_prompt_template_version` fecha a outra ponta: o DEFAULT 1 garante a primeira versão, e o
-- CHECK garante que ninguém grave 0 ou negativo — que ordenariam antes da v1 e voltariam a ser
-- lidos como "latest" por um ORDER BY malpensado.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE prompt_template
(
    id               UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at        TIMESTAMP WITHOUT TIME ZONE,
    update_at        TIMESTAMP WITHOUT TIME ZONE,
    tenant_id        UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    template         TEXT NOT NULL,
    version          INT NOT NULL DEFAULT 1,
    code             VARCHAR(255) NOT NULL,
    status           VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    params           text[],
    description      TEXT,
    tags             text[],
    inference_params JSONB,

    CONSTRAINT pk_prompt_template PRIMARY KEY (id),
    -- Uma versão por número, por code, por tenant. Só é Nullable-safety garantida porque
    -- `version` é NOT NULL: com NULL, o PostgreSQL trataria cada linha como distinta.
    CONSTRAINT uk_prompt_template_tenant_code_version UNIQUE (tenant_id, code, version),
    -- A v1 nasce do DEFAULT 1, então o piso coerente é 1. Zero ou negativo ordenariam antes
    -- da primeira versão e voltariam a ser lidos como "latest".
    CONSTRAINT chk_prompt_template_version CHECK (version >= 1)
);

-- Uma única versão ACTIVE por code, por tenant. É o que faz "ativar um prompt"
-- trocar de versão em vez de criar um segundo ACTIVE.
CREATE UNIQUE INDEX uk_prompt_template_active_version
    ON prompt_template (tenant_id, code)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_prompt_template_tenant_code
    ON prompt_template (tenant_id, code);
CREATE INDEX idx_prompt_template_tenant_status
    ON prompt_template (tenant_id, status);
CREATE INDEX idx_prompt_template_tenant_create_at
    ON prompt_template (tenant_id, create_at DESC);
