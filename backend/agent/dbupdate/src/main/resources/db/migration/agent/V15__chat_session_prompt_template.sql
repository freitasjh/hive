-- Batch 2 (2026-09-27): o conceito `Agent` morre. O prompt vira parâmetro da CONVERSA e o
-- provider/modelo vira um default do TENANT (tabela `tenant_llm_config`, módulo `tenant`).
--
-- ─────────────────────────────────────────────────────────────────────────────────────────────
-- DECISÃO SOBRE `chat_session.agent_id` — LEIA ANTES DE EDITAR
-- ─────────────────────────────────────────────────────────────────────────────────────────────
--
-- O risco: um `DROP COLUMN agent_id` destrói, em silêncio, a associação de toda sessão existente.
-- Toda sessão antiga ficaria sem prompt e o chat não teria para onde enviar a mensagem — perda de
-- dado real em qualquer ambiente com histórico, invisível no diff e irreversível.
--
-- A estratégia implementada aqui, e por que ela é reversível:
--
--   1. ADD COLUMN prompt_template_id (nullable) — nada é destruído ainda.
--   2. BACKFILL a partir de `agent.prompt_template_id`. Toda sessão cujo agente tinha template
--      herda o mesmo template. A associação é preservada por dado, não por cópia manual.
--   3. GUARD: se sobrar alguma sessão órfã (agente sem template), a migration ABORTA com uma
--      mensagem autossuficiente. O Flyway executa cada migration PostgreSQL dentro de uma
--      transação (`supportsDdlTransactions` = true), então o rollback é inteiro: NÃO EXISTE
--      estado meio-aplicado, e nenhum dado foi perdido.
--   4. Só então: a FK `fk_chat_session_agent` cai, e a coluna é RENOMEADA para `agent_id_legacy`
--      — não apagada. O dado fica. `agent_id` deixa de existir como coluna, o código para de lê-la
--      e a reversão é um `ALTER TABLE chat_session RENAME COLUMN agent_id_legacy TO agent_id`.
--   5. FK nova para `prompt_template(id)` com ON DELETE RESTRICT e SET NOT NULL.
--   6. Índice em `prompt_template_id`.
--
-- Por que abortar, e não avisar: `agent.prompt_template_id` é NULLABLE desde a V4, então
-- "agente sem template" é estado de produção realista, e cada sessão órfã viraria uma conversa
-- que o chat não consegue responder. Trocar o abort por um WARNING precisa do NÚMERO de órfãs na
-- mão e é decisão de produto, não de migration.
--
-- Depois disso, `DROP TABLE agent` (V16) pode seguir: nada mais referencia `agent` exceto a coluna
-- órfã, que não é FK.
--
-- QUANDO APAGAR `agent_id_legacy`: é uma decisão de batch próprio, com o número de sessões
-- realmente preservado em mãos. A V16 abaixo imprime a contagem.
--
-- NOTA PARA QUEM EDITAR: nunca escreva um delimitador de dollar-quote ($$) neste arquivo, nem em
-- comentário. O parser de script do Flyway corta o arquivo nesses delimitadores mesmo quando estão
-- dentro de um comentário, e o bloco é encerrado no meio. Cada bloco aninhado tem sua própria tag.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Nova coluna, nullable. Ainda nada foi perdido.
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS prompt_template_id UUID;

-- 2 + 3. Backfill e guard, no MESMO bloco: o backfill escreve na coluna que o guard lê, e um
--    bloco só evita que alguém os ordene ao contrário numa edição futura.
DO $mig$
DECLARE
    v_total        BIGINT;
    v_com_template TEXT;
    v_sem_template TEXT;
    v_msg          TEXT;
BEGIN
    IF to_regclass('public.chat_session') IS NULL THEN
        RAISE NOTICE 'V15 ignorada: tabela chat_session não existe (schema anterior à V4)';
        RETURN;
    END IF;
    IF to_regclass('public.agent') IS NULL THEN
        RAISE NOTICE 'V15: tabela agent ausente (schema anterior à V2) — backfill de prompt_template_id pulado';
        RETURN;
    END IF;

    UPDATE chat_session cs
    SET prompt_template_id = a.prompt_template_id
    FROM agent a
    WHERE a.id = cs.agent_id
      AND a.prompt_template_id IS NOT NULL
      AND cs.prompt_template_id IS DISTINCT FROM a.prompt_template_id;

    IF NOT EXISTS (SELECT 1 FROM chat_session WHERE prompt_template_id IS NULL) THEN
        -- 4. A FK e o índice antigos saem; a coluna é renomeada, nunca apagada.
        ALTER TABLE chat_session DROP CONSTRAINT IF EXISTS fk_chat_session_agent;
        DROP INDEX IF EXISTS idx_chat_session_agent;

        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'chat_session' AND column_name = 'agent_id') THEN
            ALTER TABLE chat_session RENAME COLUMN agent_id TO agent_id_legacy;
            -- A V5 deixou agent_id NOT NULL, e o RENAME carrega a constraint junto. Depois do
            -- rename a coluna é de arquivo: toda sessão nova nasce sem agente e com
            -- prompt_template_id, então NOT NULL aqui rejeitaria a inserção com um 409 de
            -- integridade que não diria nada sobre a causa real.
            ALTER TABLE chat_session ALTER COLUMN agent_id_legacy DROP NOT NULL;
            RAISE NOTICE 'V15: chat_session.agent_id renomeada para agent_id_legacy (dado preservado, NOT NULL removido)';
        END IF;

        -- 5. A coluna nova é NOT NULL e tem FK para prompt_template. RESTRICT, e não CASCADE:
        -- apagar um template em uso por uma conversa é erro, e a UI diz isso (PromptInUseException).
        ALTER TABLE chat_session ALTER COLUMN prompt_template_id SET NOT NULL;

        DO $fk$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_session_prompt_template') THEN
                ALTER TABLE chat_session ADD CONSTRAINT fk_chat_session_prompt_template
                    FOREIGN KEY (prompt_template_id) REFERENCES prompt_template (id) ON DELETE RESTRICT;
            END IF;
        END $fk$;

        -- 6. Índice da nova FK.
        CREATE INDEX IF NOT EXISTS idx_chat_session_prompt_template ON chat_session (prompt_template_id);
        RETURN;
    END IF;

    -- ── O guard disparou. Daqui para baixo, a única saída é abortar.
    --
    -- A mensagem precisa ser AUTO-SSUFICIENTE porque o rollback também desfaz o ADD COLUMN da
    -- etapa 1. Uma mensagem que mandasse o operador rodar um UPDATE sobre uma coluna que o
    -- rollback acabou de apagar seria pior que não dizer nada: daria confiança de que existe um
    -- caminho, e não existe. Por isso ela RECOMEÇA pelo ADD COLUMN, e por isso os tenants são
    -- listados — com 40 órfãs em 6 tenants, só a contagem obrigaria o operador a escrever query
    -- própria para descobrir quem é quem.
    --
    -- Os tenants vêm em DUAS listas disjuntas, e a diferença entre elas é o que torna o passo 3
    -- necessário: o UPDATE do passo 2 não tem como resolver um tenant que não tem template algum,
    -- porque o subquery correlacionado devolve NULL e a coluna continua NULL.
    SELECT
        count(*)::bigint,
        coalesce(string_agg(DISTINCT cs.tenant_id::text, ', ') FILTER (
            WHERE EXISTS (SELECT 1 FROM prompt_template pt WHERE pt.tenant_id = cs.tenant_id)), ''),
        coalesce(string_agg(DISTINCT cs.tenant_id::text, ', ') FILTER (
            WHERE NOT EXISTS (SELECT 1 FROM prompt_template pt WHERE pt.tenant_id = cs.tenant_id)), '')
    INTO v_total, v_com_template, v_sem_template
    FROM chat_session cs
    WHERE cs.prompt_template_id IS NULL;

    -- O INSERT de exemplo é montado com format() e %L de propósito: escrever as aspas do literal
    -- à mão dentro de uma string PL/pgSQL é um exercício de contagem de aspas, e errar a contagem
    -- produz uma mensagem que o operador não consegue colar. Com %L o texto sai com o literal já
    -- citado corretamente, e o operador troca <TENANT_ID> pelo tenant da lista acima.
    --
    -- A mensagem é montada numa variável e entregue com RAISE EXCEPTION '%', msg. Um RAISE com
    -- literal de formato NÃO aceita concatenação — a gramática dele é `level 'formato' [, args]` e
    -- um `||` depois do literal é erro de sintaxe. (Foi o primeiro erro desta migration.)
    v_msg :=
        'V15 ABORTADA — ' || v_total || ' sessao(oes) de chat ficariam sem prompt_template_id. NADA FOI ALTERADO.'||E'\n\n'
        ||'CAUSA: o agente que essas sessoes apontavam nao tinha prompt_template_id. A coluna e NULLABLE desde a V4, entao agente sem template e estado de producao real, nao dado corrompido.'||E'\n\n'
        ||'O ROLLBACK TAMBEM DESFEZ O PASSO 1 DESTA MIGRATION, ENTAO A COLUNA prompt_template_id NAO EXISTE MAIS. O passo 1 abaixo recria.'||E'\n\n'
        ||'SIGA NESTA ORDEM. Cada passo depende do anterior.'||E'\n\n'
        ||'1) Recrie a coluna:'||E'\n'
        ||'     ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS prompt_template_id UUID;'||E'\n\n'
        ||'2) Para cada tenant QUE TEM pelo menos um prompt template, o UPDATE abaixo resolve — o subquery correlacionado acha um template do proprio tenant.'||E'\n'
        ||'     Tenants COM template: '||coalesce(nullif(v_com_template, ''), '(nenhum)')||E'\n'
        ||'     UPDATE chat_session'||E'\n'
        ||'     SET prompt_template_id = (SELECT pt.id FROM prompt_template pt'||E'\n'
        ||'                           WHERE pt.tenant_id = chat_session.tenant_id'||E'\n'
        ||'                           ORDER BY pt.create_at LIMIT 1)'||E'\n'
        ||'     WHERE prompt_template_id IS NULL;'||E'\n\n'
        ||'3) Para cada tenant QUE NAO TEM NENHUM prompt template, CRIE UM TEMPLATE PRIMEIRO e so depois rode o passo 2. O UPDATE do passo 2 NAO resolve estes tenants: o subquery devolve NULL, a coluna continua NULL, e a V15 aborta de novo com a mesma contagem. Nao ha atalho para este caso.'||E'\n'
        ||'     Tenants SEM template: '||coalesce(nullif(v_sem_template, ''), '(nenhum)')||E'\n'
        ||'     Rode uma vez para cada tenant da lista acima, trocando <TENANT_ID> pelo id daquele tenant:'||E'\n'
        ||'     ' || format(''||E'\n'
        ||'INSERT INTO prompt_template (id, create_at, update_at, tenant_id, code, name, template, version, status) '||E'\n'
        ||'VALUES (uuid_generate_v4(), now(), now(), %L, %L, %L, %L, 1, %L);',
        '<TENANT_ID>', 'default', 'Prompt padrao', 'Voce e um assistente.', 'ACTIVE')||E'\n\n'
        ||'4) So depois dos passos 2 e 3 a V15 roda sozinha, no proximo start da aplicacao. Rodar a migration antes disso falha com esta mesma mensagem.';

    RAISE EXCEPTION '%', v_msg;
END $mig$;

-- Asserção de pós-condição. O que protege esta migration é a atomicidade da transação do Flyway em
-- PostgreSQL, não esta guarda; ela existe para falhar no startup se uma alteração futura tornar o
-- caminho do sucesso condicional e o schema ficar incompleto em silêncio.
DO $check$
BEGIN
    IF to_regclass('public.chat_session') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'chat_session' AND column_name = 'agent_id') THEN
        RAISE EXCEPTION 'V15 verification failed: chat_session.agent_id ainda existe após a migração';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_session_prompt_template') THEN
        RAISE EXCEPTION 'V15 verification failed: fk_chat_session_prompt_template ausente após a migração';
    END IF;
END $check$;
