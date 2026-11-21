# SPEC — Prompt opcional no chat (fallback automático)

> Escopo: tornar `promptTemplateId` opcional no fluxo de envio de mensagem. Sem PRD/ADR formal — mudança pequena e aditiva; ADR-020 já criou o fallback que motivou a demanda. Rastreabilidade direta ao PICCO aprovado 2026-11-21.

## 1. Architecture Decision Records (ADRs)

- **ADR-020** (contexto): criou `AssistantTurnGateway.PROMPT` (fallback em código) e a migration V8 (`system_fallback` por tenant). Esta SPEC usa esse fallback existente.
- **ADR-013 / `scope-assistant-sem-rag`** (desatualizado): dizia "não há prompt padrão; o cliente resolve o template antes de abrir a sessão" — o ADR-020 reversou esse ponto; esta SPEC registra a consequência.
- Nenhum ADR novo — campo `promptTemplateId` só deixa de ser obrigatório; contrato HTTP e schema inalterados.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context**: assistente / conversa. Isolamento por `TenantContext` herdado.
- **Tenant Provisioning / Lifecycle**: N/A.

## 3. Identity & Access Management (IAM)

- **IAM / MFA / Token / RBAC**: N/A — nenhum fluxo tocado.

## 4. Security, Key Management & Disaster Recovery

- N/A — nenhuma chave, dado ou backup alterado.

## 5. Data Model & Concurrency

- **ERD**: N/A — nenhuma tabela/coluna/migration alterada. `chat_session.prompt_template_id` continua `NOT NULL` (V4), apontando para o `system_fallback` da V8 quando o cliente não escolhe.
- **Concurrency**: inalterada.

## 6. Event Model, Integrations & Messaging Isolation

- N/A — nenhum evento introduzido.

## 7. Invariants

1. `promptTemplateId` ausente no request → sessão criada com o `system_fallback` ativo do tenant.
2. `promptTemplateId` presente → comportamento atual intacto; sessão existente permanece autoritativa.
3. Contrato HTTP inalterado (campo já era `UUID` opcional em `ChatRequestDTO:16`).
4. Nenhuma mensagem é enviada sem que a sessão tenha um template válido.

## 8. Functional Requirements

### RF-01 — Backend: fallback técnico SEM exigir prompt de banco (CORRIGIDO 2026-11-21)
**Regra de negócio (decisão humana)**: o prompt default é `AssistantTurnGateway.PROMPT` (código, ADR-020) — **não** existe mais "prompt default de banco". O registro `system_fallback` (V8) é apenas um **registro técnico para satisfazer a FK** `chat_session.prompt_template_id NOT NULL` (V4:63,70-71); seu texto é irrelevante porque `AssistantTurnGateway.systemFor` aplica `AssistantTurnGateway.PROMPT` quando o texto está em branco (`:70-72`).

Em `ChatServiceImpl.chat` (`ChatServiceImpl.java:153-178`), quando `request.getPromptTemplateId() == null` (nova sessão):
1. **NUNCA lançar "prompt de fallback não encontrado"**. `findActiveByCode` lança `PromptTemplateNotFoundException` quando ausente (`PromptTemplateServiceImpl.java:146-150`) — capturar essa exceção (ou checar existência antes) e **garantir** o registro técnico `system_fallback` do tenant.
2. Se o tenant não tem `system_fallback` ativo (ex.: V8 não rodou), **criá-lo** tenant-scoped via repository/service: code `system_fallback`, status `ACTIVE`, version 1, texto = `AssistantTurnGateway.PROMPT` (ou placeholder não vazio — `create` exige texto não blank, `PromptTemplateServiceImpl.java:43-44`). Idempotente: se já existe (outra versão/status), reativar/reusar; nunca duplicar violando o índice parcial `uk_prompt_template_active_version` (V2:92-94).
3. Usar o id do registro técnico em `chatSessionService.create(...)`. A sessão passa a ter um template válido para a FK; o system efetivo continua sendo `AssistantTurnGateway.PROMPT` (código), aplicado no gateway.

**Resultado**: enviar sem prompt **nunca** falha por falta de template default. A exceção `PromptTemplateNotFoundException` permanece apenas para o caminho de prompt **selecionado** inexistente (`:206-210`), que é erro real do cliente.

### RF-02 — Backend: manter fluxo de prompt selecionado intacto
Quando `promptTemplateId` é informado, não alterar validação/criação atual. Sessão existente continua autoritativa (`:182-204`). `resolvePromptTemplate` (`:206-210`) inalterado — continua lançando `PromptTemplateNotFoundException` se o prompt **escolhido pelo cliente** não existe (erro legítimo, diferente do fallback).

### RF-03 — Frontend: não bloquear envio sem prompt selecionado
Em `use-chat-controller.ts:243-246`, remover o guard que mostra `chat.warn.selectPrompt` e retorna. Quando `selectedPromptTemplateId` estiver vazio, **não incluir** `promptTemplateId` no request (`:270`) — o backend aplica o fallback. Manter a chave i18n `chat.warn.selectPrompt` (pode ser usada em outro lugar) ou removê-la se sem uso — decisão do implementador, sem mudar chaves outras.

### RF-04 — Frontend: manter fluxo com prompt selecionado intacto
Quando há `selectedPromptTemplateId`, enviar normalmente (comportamento atual). Seleção em sessão existente (`selectPrompt` retorna cedo com sessão aberta, `:224`) inalterada.

### NFR-01 — Contrato e restrições
Zero mudança em DTOs HTTP, schema, migrations, ChatMemory/agent/RAG/cota/classificação/MFA. `CreateSessionDTO:14` (`@NotNull`, criação direta de sessão) fora do escopo.

## 9. Critérios de Aceite Executáveis

| ID | Asserção | Verificação |
|---|---|---|
| AC-01 | Enviar sem prompt funciona | IT: `ChatService.chat` com `promptTemplateId=null` cria sessão com `system_fallback` e retorna resposta |
| AC-02 | Fallback resolvido | backend chama `findActiveByCode("system_fallback")`; sessão persistida com id do fallback |
| AC-03 | Sem fallback no banco → registro técnico criado | tenant sem `system_fallback` ativo → backend cria registro técnico idempotente; sessão é criada; system efetivo = `AssistantTurnGateway.PROMPT`; **nenhuma** exceção de "fallback não encontrado" |
| AC-04 | Seleção manual intacta | testes `ChatServiceImplTest` / `use-chat-controller.test.ts` com prompt selecionado passam |
| AC-05 | Frontend envia sem prompt | teste do controller: sem `selectedPromptTemplateId` → request sem `promptTemplateId`, sem aviso de bloqueio |
| AC-06 | Suíte verde | `mvn clean + npm test` 0 falhas (376/376 + 66/516); `mvn -Pintegration verify` 101 IT |
| AC-07 | Restrições | zero diff schema/HTTP/MFA; `code-reviewer` sem 🔴 |

## 10. Test Strategy

- **Backend**: IT cobrindo "sem prompt → fallback" e "sem fallback → erro"; testes unitários de `ChatServiceImpl` com mock do `PromptTemplateService`.
- **Frontend**: teste do controller (novo) para envio sem prompt; existentes de seleção manual preservados.
- **Estática**: AC-02..04, AC-07.
