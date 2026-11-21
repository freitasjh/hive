-- ============================================================================
-- V8 — desfaz o dado inserido pela V6 (ADR-020 RF-04), realocando as sessões
-- que apontavam para o prompt generalista padrão antes de apagá-lo.
--
-- A V6 permanece no histórico Flyway, imutável. Esta migration não cria,
-- altera ou remove tabela/coluna: apenas move dados dentro das tabelas já
-- existentes (`prompt_template` e `chat_session`).
--
-- POR QUE EXISTE O `system_fallback`
-- ───────────────────────────────────────────────────────────────────────────────
-- `chat_session.prompt_template_id` é NOT NULL e tem FK `ON DELETE RESTRICT`
-- para `prompt_template` (V4:63,70-71). Um `DELETE` direto das linhas
-- `default_general` quebra o boot com
--   "update or delete on table prompt_template violates foreign key constraint
--    fk_chat_session_prompt_template on table chat_session"
-- sempre que alguma sessão ainda apontar para o seed da V6.
-- A realocação cria, por tenant que tem `default_general`, um prompt
-- `code='system_fallback'` (`version=1`, `status='ACTIVE'`) com o MESMO texto
-- de `AssistantDefault.PROMPT` (assistant/impl/.../ai/AssistantDefault.java:51-53,
-- copiado literalmente abaixo). As sessões são redirecionadas para ele e só
-- então o `default_general` é apagado. O texto de fallback vivo em código é a
-- fonte de verdade; a linha no banco existe apenas para cumprir a integridade
-- referencial da coluna NOT NULL — não para ser lida como prompt padrão.
--
-- Regra de precedência congelada, preservada: PromptTemplate da sessão >
-- AssistantDefault.PROMPT. Um template escolhido pelo usuário não é tocado.
--
-- IDEMPOTÊNCIA E CONSTRAINTS (V2)
-- ───────────────────────────────────────────────────────────────────────────────
-- `ON CONFLICT (tenant_id, code, version) DO NOTHING` usa
-- `uk_prompt_template_tenant_code_version`; o `NOT EXISTS` cobre os casos que a
-- UNIQUE sozinha não pega: tenant que já tem versão 1 em qualquer status ou
-- já tem um ACTIVE em outra versão. Sem ele, um INSERT v1 ACTIVE estouraria o
-- índice parcial `uk_prompt_template_active_version` (uma única versão ACTIVE
-- por code/tenant). A origem dos tenants é a própria `prompt_template`, não a
-- tabela `tenant`: não há dependência cross-module nem guard `to_regclass`.
--
-- MIGRATION NOVA — AINDA NÃO APLICADA EM NENHUM AMBIENTE (corrigida em dev antes
-- do primeiro boot bem-sucedido). Se esta V8 já tiver sido registrada como
-- aplicada em algum banco local — o boot falho não deveria tê-la registrado,
-- mas Flyway registra a transação por migrator — será preciso rodar
-- `flyway repair` nesse banco antes de reintentar. Em Testcontainers o banco
-- é novo e o repair é desnecessário.
-- ============================================================================

DO $$
BEGIN
    INSERT INTO prompt_template
        (tenant_id, name, template, version, code, status, params, create_at, update_at)
    SELECT d.tenant_id,
           'Prompt de sistema (fallback)',
           'Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, '
               || 'de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe '
               || 'em vez de inventar. Mantenha o tom profissional e cordial.',
           1,
           'system_fallback',
           'ACTIVE',
           NULL,
           now(),
           now()
    FROM (SELECT DISTINCT tenant_id FROM prompt_template WHERE code = 'default_general') d
    WHERE NOT EXISTS (
        SELECT 1
        FROM prompt_template p
        WHERE p.tenant_id = d.tenant_id
          AND p.code = 'system_fallback'
          AND (p.version = 1 OR p.status = 'ACTIVE')
    )
    ON CONFLICT (tenant_id, code, version) DO NOTHING;
END $$;

-- Redireciona toda sessão cujo template é o default_general para o
-- system_fallback DO MESMO tenant. Se o fallback não existir, nenhuma linha é
-- atualizada e o DELETE abaixo falha com FK — preferível a deixar órfão.
UPDATE chat_session cs
SET prompt_template_id = sf.id
FROM prompt_template dg
JOIN prompt_template sf
  ON sf.tenant_id = dg.tenant_id
 AND sf.code = 'system_fallback'
 AND sf.status = 'ACTIVE'
WHERE cs.prompt_template_id = dg.id
  AND dg.code = 'default_general';

-- Agora seguro: nenhuma sessão viva referencia mais estas linhas.
DELETE FROM prompt_template WHERE code = 'default_general';
