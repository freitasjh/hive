# ADR 007: Chat Persistence & Multi-turn com TX Split

## Status
Accepted — 2026-08-26 (mvp-chat Fase 0)

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. O que aconteceu depois está em
> `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag` (Brain).
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> A RN2 "agentId imutável por sessão" foi substituída pela RN-PT2: a sessão guarda
`prompt_template_id` e o prompt é o parâmetro do turno, escolhido por conversa. As rotas `/v1/agents`
não existem mais. A parte de persistência de sessão e mensagem continua válida — inclusive a
separação de transações.


## Context
Chat era stateless (ChatServiceImpl sem persistência), frontend hardcoded. V4 criou tabelas chat_session/chat_message sem wiring. Necessário persistência multi-turn, session CRUD, window 20, isolamento tenant.

## Decisão
- **Entidades** `ChatSessionEntity`/`ChatMessageEntity` extend `AbstractEntity` (tenantId + createAt/updateAt + @Filter), `FetchType.LAZY` em ManyToOne, `@Enumerated(STRING)` role USER/ASSISTANT, title VARCHAR(50) NOT NULL.
- **V5 corretiva idempotente** `V5__fix_chat_session_message_constraints_and_indexes.sql`: LEFT(title,50), UPPER(role) legacy, CHECK ('USER','ASSISTANT'), TEXT content, FK chat_message.session_id ON DELETE CASCADE, tenant_id NOT NULL, 7 índices (tenant+user, session, agent, updateAt, createAt).
- **TX split** em `ChatServiceImpl.chat()`: TX1 save userMsg (commit), fora TX findLast20 (window 20 DESC+reverse ASC) + provider.chat(history), TX2 `ChatAssistantTxService.saveAssistantAndTouch` atômico (assistant + touch updateAt). Orquestrador sem @Transactional. Falha provider preserva TX1, retorna 502.
- **Window 20** últimas mensagens ordenadas ASC para LLM.
- **RN1 title** `truncate(trim(message),50)` sem "...", fallback "Nova conversa", codePoints surrogate-safe via ChatTitleUtil.
- **RN2 agentId imutável** por sessão — divergente ignorado.
- **Anti-IDOR 404** via findByIdAndTenantIdAndUserId em todos acessos por id, tenant filter em toda query.
- **API** 7 endpoints sob /hive/v1/chat + /v1/agents: POST /chat (sessionId opcional 200), POST /chat/sessions 201, GET /chat/sessions Page DESC 200, GET /chat/sessions/{id} 200/404, GET /chat/sessions/{id}/messages Page ASC 200/404, DELETE 204 cascade, PATCH rename 200, GET /v1/agents Page 200. Todos @HasPermission(CHAT_SEND/CHAT_SESSION_MANAGE) via HasPermissionAspect @Aspect + @EnableAspectJAutoProxy.
- **Auth** authorities vazias bypass WARN (débito RBAC até Fase 1), anti-IDOR mitiga.
- **Frontend** session.service/chat.service/agent.service via api.ts baseURL /hive/v1, AgentSelector (Select PrimeVue, RN2 disabled), ChatSidebar (Drawer, ScrollPanel, Paginator 20, Dialog confirm delete), Chat.vue (localStorage hive_sessionId, loadSeq guard, messages ASC, send/newChat/delete, scrollToBottom).

## Alternativas descartadas
- Event-sourcing/outbox para chat — over-engineering Fase 0.
- TX única englobando LLM — lock longo, rollback userMsg em falha provider.
- Soft-delete — MVP hard delete cascade (LGPD soft depois).

## Consequências
- Prós: persistência, reload CA10, sidebar real CA3-6, multi-turn CA2, isolamento tenant CA8, paginação determinística.
- Contras: RBAC incompleto (bypass), integration-test módulo ausente, flatten-maven-plugin pendente (backend não sobe standalone), AgentService.findById tenant-blind.
