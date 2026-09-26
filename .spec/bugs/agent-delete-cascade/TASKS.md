# BUG — Agente com histórico de chat não pode ser excluído (409)

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (funcionalidade inutilizável; o usuário não consegue excluir nenhum agente)
- **Origem:** reporte do usuário · reproduzido em tela/API antes de codar
- **Decisão:** usuário aprovou **A1** — `ON DELETE CASCADE`

## Reprodução

```
DELETE /hive/v1/agents/867f0342-b6e8-42b9-8059-54468a20955c  →  409
log: update or delete on table "agent" violates foreign key constraint "fk_chat_session_agent"
     Key (id)=(867f0342…) is still referenced from table "chat_session"
resposta ao usuário: "Violação de integridade de dados."   ← vazamento cru, sem orientação
```

**Todos** os agentes do tenant têm sessão de chat (8 agentes, 1 a 13 sessões cada) → **nenhum** é
excluível. O usuárioRelata corretamente.

## Causa raiz

`chat_session.agent_id` tem FK para `agent(id)` **sem cláusula `ON DELETE`** → `NO ACTION`.
Qualquer agente com histórico bloqueia a exclusão.

Mapeamento completo da cadeia (verificado no banco):

| FK | Direção | ON DELETE | Situação |
|---|---|---|---|
| `fk_chat_message_session` | `chat_message.session_id → chat_session.id` | **CASCADE** ✅ | já ok |
| `fk_agent_kb_kb` | `agent_knowledge_base.kb_id → knowledge_base.id` | **CASCADE** ✅ | já ok |
| `fk_chat_session_agent` | `chat_session.agent_id → agent.id` | **NO ACTION** ❌ | **o bloqueio** |
| `fk_agent_prompt_template` | `agent.prompt_template_id → prompt_template.id` | NO ACTION | não bloqueia: o filho é o `agent` |

### 🔴 Achado adicional (o mesmo bug, ponta oculta)
**`agent_knowledge_base.agent_id` NÃO tem foreign key para `agent`.** Só índice btree:

```
Foreign-key constraints:
  "fk_agent_kb_kb" FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
  (nada para agent_id)
```

Consequências:
1. Hoje a exclusão de agente é bloqueada pela sessão, então os links órfãos **não** aparecem — mas
   assim que a migration do cascade entrar, os links de KB **vão ficar órfãos**, apontando para um
   agente inexistente, e o `linkedKbNames` pode devolver vínculo de agente morto.
2. A integridade hoje depende **só** da camada de aplicação.
3. A detecção de "espaço misturado" e o anti-IDOR do KB passam a ter uma tabela com lixo.

**A migration tem que fechar os dois**, senão o cascade troca um 409 por inconsistência silenciosa.

## Solução

### Migration `V14__agent_delete_cascade.sql` (módulo `agent`)
> Próxima versão livre do módulo `agent` é **V14** (existem V2..V10; `knowledge` tem V11..V13 em
> módulo próprio). **Nunca alterar migration aplicada** — criar nova.

1. `chat_session.agent_id → agent(id)` com **ON DELETE CASCADE**.
2. `agent_knowledge_base.agent_id → agent(id)` com **ON DELETE CASCADE** (fecha o achado acima).
   - A tabela está no módulo `knowledge` (`V11__rag_kb_core.sql` criou), mas a FK nova é sobre
     `agent`. Decidir e **documentar** em qual arquivo de migração ela vive: o `agent_knowledge_base`
     é criado pelo módulo `knowledge`, então a FK deve ir no **módulo `knowledge`** (`V14__…`) para
     manter a ownership da tabela — porém a regra do projeto diz que cada módulo tem histórico
     próprio e a FK referencia `agent` de outro módulo. **Avalie as duas opções e justifique**;
     recommendation: no módulo que **criou a tabela** (`knowledge`), porque é lá que o
     `agent_knowledge_base` é-indexado e o `agent` já existe quando `knowledge` migra.
3. Idempotente: `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT … ON DELETE CASCADE`.
4. **Não** criar índice novo (os btree já existem).

### Erro 409 → mensagem acionável
Hoje a resposta é *"Violação de integridade de dados."* — texto cru do banco, sem orientação.
- Com o cascade, a exclusão passa a funcionar e essa mensagem deixa de aparecer no caminho comum.
- **Ainda assim**: qualquer outra violação de integridade deve responder com mensagem pt-BR
  interpretável, **sem vazar nome de tabela/coluna/FK** para o cliente (A05). O detalhe fica no log.
- Verificar se `ControllerExceptionHandler` já trata `DataIntegrityViolationException` de forma
  genérica; se vaza `pg_get_constraintdef`, **é vazamento de informação de schema**.

### Documentação do comportamento destrutivo
Excluir agente **apaga** suas sessões e mensagens permanentemente. Registrar em:
- `docs/product/SYSTEM_SPECIFICATION.md` (§4.2 ou a seção de KB/RAG): "excluir agente remove em
  cascata suas sessões e mensagens; não há recuperação".
- Frontend: o diálogo de confirmação deve dizer isso (hoje `ConfirmDialog` genérico). **Verificar o
  que a tela mostra** e deixar explícito.

## Testes
1. **IT (o critério de aceite)**: criar agente → criar sessão → enviar mensagem → `DELETE /agents/{id}`
   → **204**; e `chat_session`/`chat_message` do agente **não** existem mais; e
   `agent_knowledge_base` do agente **não** existe mais.
2. Agente **sem** sessão → 204 (não regredir).
3. Agente de **outro tenant** → **404** (anti-IDOR; não pode apagar agente alheio).
4. `DataIntegrityViolationException` por outro caminho → mensagem pt-BR genérica, **sem** nome de
   tabela/coluna/FK no corpo da resposta (assert explícito).
5. Frontend: spec do diálogo de confirmação assertando que ele menciona a remoção das conversas.
6. H1 completo: `mvn clean install -DskipTests` + `mvn test` +
   `mvn verify -pl integration-test -Pintegration -am` → 0 falhas · `npm run test` + `npm run build` → 0 falhas.

## ⚠️ Irreversibilidade (confirmada pelo usuário)
`ON DELETE CASCADE` significa que **a exclusão de um agente destrói permanentemente o histórico de
conversas**. Não há lixeira nem recuperação. O usuário aprovou explicitamente (opção A1), e a
mitigação é a mensagem explícita na confirmação da tela.

## Critérios de aceite
- [ ] Migration nova (não alterar aplicada), idempotente
- [ ] `chat_session.agent_id` e `agent_knowledge_base.agent_id` com CASCADE
- [ ] 204 ao excluir agente com sessão + dados realmente removidos
- [ ] 404 anti-IDOR
- [ ] Sem vazamento de schema na resposta de erro
- [ ] Documentação do comportamento destrutivo (spec + diálogo da tela)
- [ ] Suite completa 0 falhas
- [ ] `fullstack-code-reviewer` zero 🔴
- [ ] Brain (MCP fora — deixar nota)
- [ ] Processos finalizados
