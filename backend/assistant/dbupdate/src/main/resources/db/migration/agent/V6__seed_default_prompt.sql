-- ============================================================================
-- V6 — seed de um prompt generalista ATIVO por tenant existente.
--
-- POR QUE V6 (e não V4)
-- ───────────────────────────────────────────────────────────────────────────────
-- O histórico do módulo `agent` já tem V2 (prompt_template), V3
-- (provider_credential), V4 (chat_session) e V5 (chat_message). A V4 de
-- `identity_db` (user_mfa) é de OUTRO módulo, com outra tabela de histórico
-- Flyway (`flyway_schema_history_identity`) — não ocupa a numeração do agent.
-- A próxima versão livre aqui é, portanto, V6.
--
-- POR QUE `tenant` COMO ORIGEM
-- ───────────────────────────────────────────────────────────────────────────────
-- Existem duas tabelas com tenant_id no banco: `public.users` (identity, onde
-- tenant_id é anulável) e `public.tenant` (tenant_db V2, o cadastro canônico,
-- com id NOT NULL). Usamos `tenant`: é o registro dos tenants que existem, e
-- não um subproduto de usuário. `DISTINCT tenant_id FROM users` aceitaria um
-- tenant órfão de usuário e exigiria filtrar NULL; aqui a chave já é única.
--
-- NENHUM OUTRO MÓDULO É ESCRITO
-- ───────────────────────────────────────────────────────────────────────────────
-- Só `agent.prompt_template` recebe INSERT. Não há FK cross-module, não há
-- trigger e não há escrita em `tenant`/`users`: os históricos Flyway são
-- independentes e a ordem entre os beans não é garantida. O guard
-- `to_regclass` abaixo existe exatamente por isso — se o módulo tenant ainda
-- não tiver criado a tabela quando esta migration rodar, o boot não morre.
--
-- LIMITAÇÃO DOCUMENTADA
-- ───────────────────────────────────────────────────────────────────────────────
-- O seed cobre APENAS os tenants presentes no momento em que a migration
-- roda. Tenant criado depois NÃO recebe prompt automaticamente: criar um
-- trigger cross-module exigiria decisão de arquitetura e não está neste
-- escopo. Reexecutar este SQL (ou criar uma migration de data-backfill
-- posterior) é a forma de semear tenants novos.
--
-- Se o guard `to_regclass` retornar NULL (tenant ainda inexistente quando o
-- agent migra primeiro), o Flyway AINDA registra V6 como aplicada. Não há
-- backfill automático nesse deploy: reexecutar este SQL é obrigatório. A ordem
-- entre `agentFlyway` e `flywayTenant` não é garantida (sem @DependsOn) — não
-- confie no boot.
--
-- IDEMPOTÊNCIA E CONSTRAINTS
-- ───────────────────────────────────────────────────────────────────────────────
-- `ON CONFLICT (tenant_id, code, version) DO NOTHING` usa a UNIQUE da V2.
-- O `NOT EXISTS` fecha os dois casos que a UNIQUE sozinha não cobre: um
-- tenant que já tem versão 1 (qualquer status) ou já tem um ACTIVE em outra
-- versão — nesse caso um INSERT v1 ACTIVE estouraria o índice parcial
-- `uk_prompt_template_active_version`. Resultado: um prompt por tenant,
-- version = 1, status = ACTIVE, template não vazio.
-- ============================================================================

DO $$
BEGIN
    -- Histórios Flyway independentes: `tenant` pode ainda não existir quando o
    -- bean do agent migrar. Sem o guard, o boot quebraria com "relation tenant
    -- does not exist" — e um seed não pode ser motivo de falha de startup.
    IF to_regclass('public.tenant') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO prompt_template
        (tenant_id, name, template, version, code, status, params, create_at, update_at)
    SELECT t.id,
           'Prompt generalista padrão',
           'Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, '
               || 'de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe '
               || 'em vez de inventar. Mantenha o tom profissional e cordial.',
           1,
           'default_general',
           'ACTIVE',
           NULL,
           now(),
           now()
    FROM tenant t
    WHERE NOT EXISTS (
        SELECT 1
        FROM prompt_template p
        WHERE p.tenant_id = t.id
          AND p.code = 'default_general'
          AND (p.version = 1 OR p.status = 'ACTIVE')
    )
    ON CONFLICT (tenant_id, code, version) DO NOTHING;
END $$;
