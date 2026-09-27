-- Batch 2 (2026-09-27): `agent` sai do produto. A configuração de LLM passou para
-- `tenant_llm_config` (módulo `tenant`) e o prompt passou a ser escolhido por conversa.
--
-- A V15 já removeu a FK `chat_session.agent_id -> agent(id)` e guardou o dado em
-- `chat_session.agent_id_legacy`. Esta migration apaga a tabela e as colunas que eram só dela:
-- `tools` e `knowledge_bases` (V7), `is_default` (V6), `prompt_template_metadata` (V4), e o resto
-- de `name/description/llm_model/provider_type/temperature/active/version/avatar_url/system_prompt/
-- top_p/max_tokens/prompt_template_id` — um `DROP TABLE` leva a coluna junto, e vale dizer quais
-- são para que a lista não pareça arbitrária.
--
-- RESTRICT em vez de CASCADE, deliberadamente: se sobrar alguma referência de qualquer tabela
-- viva para `agent`, o startup falha com o nome da constraint em vez de derrubar dado que ninguém
-- pediu para derrubar. A V15 é a única fonte de referência conhecida e já foi removida antes.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
BEGIN
    IF to_regclass('public.agent') IS NULL THEN
        RAISE NOTICE 'V16 ignorada: tabela agent não existe';
        RETURN;
    END IF;

    RAISE NOTICE 'V16: % sessao(oes) com agent_id_legacy preservado para auditoria',
            (SELECT count(*) FROM chat_session WHERE agent_id_legacy IS NOT NULL);

    DROP TABLE agent;
END $$;
