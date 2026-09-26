-- BE-11: excluir agente com histórico de chat era impossível (409 fk_chat_session_agent).
--
-- `chat_session.agent_id` referenciava `agent(id)` sem cláusula ON DELETE, ou seja NO ACTION: com
-- uma única sessão criada, o agente ficava travado. No tenant de desenvolvimento nenhum dos 8
-- agentes era excluível.
--
-- ON DELETE CASCADE é DESTRUTIVO: excluir um agente apaga permanentemente suas chat_session e, em
-- cascata, as chat_message delas. Não há lixeira nem recuperação. Decisão do usuário (opção A1,
-- 2026-09-26); a mitigação é o aviso explícito no diálogo de confirmação da tela de agentes.
--
-- Idempotente: DROP IF EXISTS + ADD. Nunca alterar migration aplicada — esta é a V14.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Só recria a FK se a tabela existir. Um banco criado antes da V4 (chat_session) não tem o que
-- cascatear, e falhar aqui derrubaria o startup inteiro por causa de um schema legado.
DO $$
BEGIN
    IF to_regclass('public.chat_session') IS NULL THEN
        RAISE NOTICE 'V14 ignorada: tabela chat_session não existe (schema anterior à V4)';
        RETURN;
    END IF;

    ALTER TABLE chat_session DROP CONSTRAINT IF EXISTS fk_chat_session_agent;
    ALTER TABLE chat_session ADD CONSTRAINT fk_chat_session_agent
        FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE;
END $$;

-- Asserção de pós-condição sobre o DDL aplicado acima. O que protege esta migration não é esta
-- guarda: é a atomicidade da transação. O Flyway executa cada migration PostgreSQL dentro de uma
-- transação, então um DROP/ADD que falhou foi revertido por inteiro e a constraint anterior (NO
-- ACTION) continua intacta — nunca um estado meio-aplicado. A guarda não "salva" uma migration
-- falha; ela existe para falhar no startup se uma alteração futura tornar o ADD condicional e a
-- constraint ficasse ausente em silencioso.
DO $$
BEGIN
    IF to_regclass('public.chat_session') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_chat_session_agent') THEN
        RAISE EXCEPTION 'V14 verification failed: fk_chat_session_agent ausente após a migração';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'fk_chat_session_agent' AND confdeltype = 'c') THEN
        RAISE EXCEPTION 'V14 verification failed: fk_chat_session_agent não está com ON DELETE CASCADE';
    END IF;
END $$;
