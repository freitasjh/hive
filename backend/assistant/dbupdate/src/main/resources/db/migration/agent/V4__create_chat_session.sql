-- ============================================================================
-- V4 — chat_session, no formato final.
--
-- Histórico reescrito em 2026-09-27. A tabela nasce aqui completa e já sem
-- `agent_id`. Não existe etapa que crie `agent_id` e outra que a renomeie ou
-- apague.
--
-- ───────────────────────────────────────────────────────────────────────────────
-- POR QUE NÃO EXISTE `agent_id`, E POR QUE NEM `agent_id_legacy`
-- ───────────────────────────────────────────────────────────────────────────────
-- O conceito `Agent` saiu do produto: a configuração de LLM passou para
-- `tenant_llm_config` (módulo `tenant`) e o prompt passou a ser parâmetro da
-- CONVERSA. A sessão aponta direto para o template que a responde.
--
-- No histórico anterior isso levava três migrations sobre a mesma coluna — a
-- V4 criava `agent_id` nullable, a V5 tentava torná-lo NOT NULL, a V14
-- pendurava `ON DELETE CASCADE` nele, a V15 fazia backfill de
-- `prompt_template_id` a partir de `agent.prompt_template_id`, renomeava
-- `agent_id` para `agent_id_legacy` e derrubava a FK, e a V16 dropava a tabela
-- `agent`. Um rastro de cinco versões para chegar onde esta migration chega em
-- uma.
--
-- `agent_id_legacy` NÃO é recriado, e essa é a diferença real entre o schema
-- antigo e este (o resto é idêntico — ver o diff no relatório do batch). A
-- coluna existia para preservar, em reversível, a associação das sessões que
-- apontavam para um agente; sem tabela `agent`, o dado que ela guardaria não
-- tem significado, e o histórico de dev foi descartado por decisão do usuário
-- (2026-09-27). Preservar um UUID de agente que não resolve para nada seria
-- uma coluna que a aplicação nunca lê e que ninguém pode validar.
--
-- O guard anti-perda da V15 (que abortava o boot com 58 sessões órfãs) não tem
-- o que proteger aqui: não há `agent` de onde a sessão herdar template, e não
-- há backfill porque não há coluna antiga. A perda deixa de ser possível por
-- construção do schema, e não por uma verificação rodada no boot.
--
-- ───────────────────────────────────────────────────────────────────────────────
-- NOTA (débito, não corrigido aqui): `summary` é `VARCHAR(2000)` no schema
-- desde a V4, enquanto `ChatSessionEntity` declara `columnDefinition = "TEXT"`.
-- Com `ddl-auto=none` o Hibernate não valida, então o banco recusa um summary
-- acima de 2000 caracteres que a entidade juraria aceitar. O histórico antigo
-- mantinha 2000 e a entidade mantinha TEXT; reproduzir 2000 é a escolha
-- conservadora (é o que a série anterior produzia), e alinhar os dois é batch
-- próprio.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE chat_session
(
    id                 UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at          TIMESTAMP WITHOUT TIME ZONE,
    update_at          TIMESTAMP WITHOUT TIME ZONE,
    tenant_id          UUID NOT NULL,
    user_id            UUID NOT NULL,
    title              VARCHAR(50) NOT NULL,
    summary            VARCHAR(2000),
    -- Toda sessão tem template desde o nascimento: é ele que a responde. Por
    -- isso NOT NULL no CREATE TABLE, e não um SET NOT NULL depois de um
    -- backfill. O título tem 50 caracteres porque é o que a sidebar mostra sem
    -- truncar, e o default 'Nova conversa' do histórico anterior era um
    -- preenchimento de dado sujo — aqui o NOT NULL é a regra e o chamador
    -- provide o título.
    prompt_template_id UUID NOT NULL,

    CONSTRAINT pk_chat_session PRIMARY KEY (id),
    -- RESTRICT, e não CASCADE: apagar um template em uso por uma conversa é
    -- erro de produto, e a UI diz isso (PromptInUseException). A distinção com
    -- chat_message é deliberada — apaga-se a conversa e as mensagens dela vão
    -- junto, mas não se apaga o prompt que dá sentido a uma conversa viva.
    CONSTRAINT fk_chat_session_prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_template (id) ON DELETE RESTRICT
);

CREATE INDEX idx_chat_session_tenant_user
    ON chat_session (tenant_id, user_id);
CREATE INDEX idx_chat_session_prompt_template
    ON chat_session (prompt_template_id);
-- Índices de ordenação da sidebar, que lista por data decrescente.
CREATE INDEX idx_chat_session_update_at
    ON chat_session (update_at DESC);
CREATE INDEX idx_chat_session_create_at
    ON chat_session (create_at DESC);
