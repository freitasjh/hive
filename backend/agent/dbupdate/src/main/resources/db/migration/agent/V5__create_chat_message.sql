-- ============================================================================
-- V5 — chat_message, no formato final.
--
-- Histórico reescrito em 2026-09-27. A tabela nasce aqui completa, com o
-- `ON DELETE CASCADE` para a sessão — que era o motivo de a V5 existir.
--
-- O que a série anterior fazia em cinco migrations:
--   · V2  criava a tabela com content VARCHAR(8000) e session_id solto
--   · V4  Adding metadata JSONB + FK para chat_session
--   · V5  content → TEXT NOT NULL, tenant_id NOT NULL, recriava a FK COM
--        ON DELETE CASCADE, e ainda tentava pôr agent_id NOT NULL
--
-- ───────────────────────────────────────────────────────────────────────────────
-- POR QUE O CASCADE ESTÁ AQUI, E NÃO NUMA MIGRATION DEPOIS
-- ───────────────────────────────────────────────────────────────────────────────
-- A intenção de produto: apagar uma conversa apaga as mensagens dela, e nunca
-- deixa órfã. Na série antiga isso exigiu um `DROP CONSTRAINT` seguido de
-- `ADD CONSTRAINT` dentro de uma migration chamada `fix_...` — trabalho de
-- conserto porque a constraint nasceu errada. Aqui a constraint é criada já com
-- a cláusula que expressa a regra, uma vez só.
--
-- `content` é TEXT, não VARCHAR(8000): resposta de LLM passa de 8000
-- caracteres com frequência, e VARCHAR(8000) quebrava a gravação com um erro de
-- dimensão que não dizia nada sobre a causa. A regra de histórico anterior já
-- convertia para TEXT; o que muda é que nasce TEXT.
--
-- ───────────────────────────────────────────────────────────────────────────────
-- NOTA (débito, não corrigido aqui): `role` é `VARCHAR(255)` no schema desde a
-- V2, enquanto `ChatMessageEntity` declara `length = 20`. O CHECK abaixo é o
-- que realmente restringe o valor a 'USER'/'ASSISTANT', então a divergência de
-- largura é inofensiva — mas o contrato está em dois lugares de novo. Alinhar
-- é batch próprio.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE chat_message
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID NOT NULL,
    session_id  UUID NOT NULL,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    metadata    JSONB,

    CONSTRAINT pk_chat_message PRIMARY KEY (id),
    -- Apagar a sessão leva as mensagens junto. É o comportamento que a UI de
    -- exclusão de conversa promete e que o usuário não precisa ser avisado.
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE,
    -- O enum ChatRole é USER/ASSISTANT. O histórico antigo normalizava valores
    -- minúsculos ('user'/'assistant') em um UPDATE antes de criar o CHECK,
    -- porque a V2 aceitou os dois; nascendo aqui, o CHECK é a única regra e
    -- não existe grafia errada a normalizar.
    --
    -- `role` é VARCHAR(20) e não VARCHAR(255) (corrigido 2026-09-27): a largura
    -- 255 era herança da V2, anterior ao CHECK, e ficou divergente de
    -- `ChatMessageEntity.role`, que declara length = 20. O CHECK é a regra que
    -- realmente restringe (2 valores, o maior com 9 caracteres), então alinhar
    -- a largura não muda o que é aceito — só faz o DDL e a entidade contarem a
    -- mesma história.
    CONSTRAINT chk_chat_message_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_chat_message_session_id
    ON chat_message (session_id);
CREATE INDEX idx_chat_message_tenant_session
    ON chat_message (tenant_id, session_id);
CREATE INDEX idx_chat_message_create_at
    ON chat_message (create_at);
