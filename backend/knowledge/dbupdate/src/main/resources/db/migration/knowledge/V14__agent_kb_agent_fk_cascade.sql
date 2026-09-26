-- BE-11 (parte 2): fecha o link órfão de `agent_knowledge_base.agent_id`.
--
-- A junção era criada pela V11 com FK apenas para `knowledge_base`; `agent_id` ficava sem foreign key,
-- sustentado só pela camada de aplicação. Isso era invisível enquanto a exclusão de agente era
-- bloqueada pela sessão (409 em `chat_session`), mas com o CASCADE da V14 do módulo `agent` os
-- vínculos de KB passariam a apontar para agentes inexistentes — trocando um 409 explícito por
-- inconsistência silenciosa.
--
-- Por que a FK vive aqui, no módulo `knowledge`, e não no `agent`: `agent_knowledge_base` é tabela
-- do módulo `knowledge` (V11 a criou, `AgentKbEntity` a mapeia, `KbAgentLinkPort` é a fronteira
-- agent -> knowledge). A FK é parte da integridade da tabela dona, então nasce com a dona.
-- A dependência cruzada knowledge -> agent já existia: a V13 lê `FROM agent` para o backfill de
-- `Agent.knowledgeBases`. Por isso esta migration NÃO tem guarda de "tabela agent existe": se
-- `agent` não existisse, a V13 já teria falhado antes. A ordem de execução entre os dois módulos
-- passou a ser explícita em `KnowledgeDbUpdateConfig` (@DependsOn("agentFlyway")).
--
-- ON DELETE CASCADE é IRREVERSÍVEL: excluir um agente remove também, e de forma permanente, os
-- vínculos dele com bases de conhecimento. A purga de órfãos do passo 1 também é irreversível — as
-- linhas não têm volta. Não há lixeira, soft delete nem recuperação. Decisão do usuário (A1,
-- 2026-09-26); a mitigação para o usuário final é o aviso no diálogo de confirmação da tela de
-- agentes. Reversibilidade aqui só via backup do banco.
--
-- Idempotente: a purga de órfãos é reexecutável e o par DROP IF EXISTS + ADD recria a constraint.

-- 1. Purga vínculos órfãos. Sem FK, nada impedia que sobrasse linha com agent_id de um agente já
--    removido; o ADD CONSTRAINT abaixo falharia em cima delas. Tabelas de junção: o dado é
--    derivável (o vínculo), não estado de negócio — descartar é seguro.
--    Esta é a ÚNICA instrução destrutiva do change, então ela não roda em silêncio: se apagar
--    alguma coisa, diz no log quantas linhas foram. Sem isso, uma purga de N vínculos em produção
--    seria indistinguível de uma purga de zero.
DO $$
DECLARE
    purged integer;
BEGIN
    DELETE FROM agent_knowledge_base akb
    WHERE NOT EXISTS (SELECT 1 FROM agent a WHERE a.id = akb.agent_id);

    GET DIAGNOSTICS purged = ROW_COUNT;
    IF purged > 0 THEN
        RAISE NOTICE 'V14 (knowledge): % vínculo(s) órfão(s) removido(s) de agent_knowledge_base', purged;
    END IF;
END $$;

-- 2. FK com CASCADE: excluir agente leva junto os vínculos.
ALTER TABLE agent_knowledge_base DROP CONSTRAINT IF EXISTS fk_agent_kb_agent;
ALTER TABLE agent_knowledge_base ADD CONSTRAINT fk_agent_kb_agent
    FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE;

-- 3. Asserção de pós-condição sobre o DDL aplicado acima. O que protege esta migration não é esta
--    guarda: é a atomicidade da transação. O Flyway executa cada migration PostgreSQL dentro de uma
--    transação, então se o DROP/ADD acima tivesse falhado, tudo aqui teria sido revertido e a
--    constraint anterior (inexistente) teria sido preservada — nunca um estado meio-aplicado. Esta
--    guarda não "salva" uma migration falha. Ela existe para falhar no startup se uma alteração
--    futura tornar o ADD condicional (por exemplo dentro de um `to_regclass`) e a constraint
--    faltasse em silencioso.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'fk_agent_kb_agent' AND confdeltype = 'c') THEN
        RAISE EXCEPTION 'V14 (knowledge) verification failed: fk_agent_kb_agent ausente ou sem ON DELETE CASCADE';
    END IF;
END $$;
