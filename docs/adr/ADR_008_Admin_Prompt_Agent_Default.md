# ADR 008: Administração de Prompts e Agentes + Agente Padrão

## Status
Accepted — 2026-08-30 (admin-prompt-agent)

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. O que aconteceu depois está em
> `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag` (Brain).
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> O conceito de **Agent** morreu por completo: entidade, serviço, controller, mappers, DTOs, tabela
`agent` (`DROP TABLE`, `agent/V16`) e `chat_session.agent_id`. A sessão aponta para
`prompt_template_id` (`agent/V15`, que **preserva** o dado antigo em `agent_id_legacy`) e o modelo
virou `tenant_llm_config`. Não há mais "agente padrão" para resolver: o seletor de prompt do chat é
o prompt do turno, e uma sessão o mantém imutável. A parte de **versionamento de prompt** deste ADR
sobrevive.


## Context
Sistema carecia de interfaces admin para prompts/agentes e de mecanismo fallback quando agentId não informado. Sem padronização, promopt link, exclusividade default e fallback falhavam.

## Decisão
- **Exclusividade Default**: Partial unique index `uq_agent_default_per_tenant ON agent(tenant_id) WHERE is_default=true AND active=true` + single-statement split 2 UPDATEs `clearDefaultByTenantId(false) WHERE is_default=true` + `setDefaultById(true) WHERE id` em `@Transactional` com `clearAutomatically flushAutomatically`, catch `DataIntegrityViolation → CONCURRENT_DEFAULT_UPDATE 409`.
- **Resolver**: `DefaultAgentResolver`/`DefaultAgentService` injetado em `ChatServiceImpl`, `requireDefault(tenantId)` → 422 `DEFAULT_AGENT_NOT_FOUND` se ausente, `GET /default` 404 query vs `POST /chat null` 422 command.
- **Validação Template**: Regex canônica `\{\{\s*[a-zA-Z_][a-zA-Z0-9_]*\s*\}\}` em `PromptVariableExtractor` BE + `promptVars.ts` FE, server authoritative `params = extract(template)`, code imutável, version++.
- **Proteção Sole**: `countByTenantIdAndIsDefaultTrue==1 && isDefault` → 409 `SOLE_DEFAULT_AGENT` em delete/desativação, FE badge ⭐ Padrão warn Amber + Dialog "Já existe padrão. Trocar?" + disabled sole.
- **RBAC**: `@HasPermission("PROMPT_MANAGE"/"AGENT_MANAGE")` + `anyOf={"CHAT_SEND","AGENT_MANAGE"}` para GET /agents, JWT `permissions` claim via SecurityTokenService, HasPermissionAspect `clearAutomatically`.
- **Migrations**: V6 `is_default/avatar/systemPrompt` + partial unique, V7 `prompt description/tags/inference_params/TEXT`, V8 FK RESTRICT `ON DELETE RESTRICT` + verification, idempotente `IF NOT EXISTS`/`DO $$`.
- **Frontend**: DataTable I1, Split 60/40 I2, Template highlight I3 cyan, Playground I4 mock local `renderPreview` sem LLM, Sliders I5, Badge I6, Toggle I7, Multi-select I8, grid `col-12 md:col-6` 4 breakpoints, 5 telas spec, PrimeVue Aura Pulse.
- **Herança**: Agent inferenceParams sobrescreve Prompt, systemPrompt prioridade `agent.systemPrompt > prompt.template`.

## Alternativas rejeitadas
- Apenas flag app sem índice → race 2 defaults
- OrchestratorService separado → over-engineering, YAGNI
- Frontend LLM playground → custo, P2 deferred

## Consequências
- Prós: fallback garantido, exclusividade atômica, playground sem custo, responsivo, i18n PT-BR, 209 tests 16 E2E pass
- Contras: rate limiting dívida Bucket4j, actuator permitAll débito, frontend projeto errado corrigido
