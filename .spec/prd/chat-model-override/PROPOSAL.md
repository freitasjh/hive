# Override de provedor/modelo por conversa no chat — PROPOSAL

> Status: aprovado para implementação · Data: 2026-11-20 · Branch: feat/chat-ai-elements
> Decisor: operador (João) · Escopo: backend Spring Boot (módulo agent) + contrato HTTP do chat

## 1. Decisões do operador (2026-11-20)
1. Chat no padrão shadcn AI Elements — elementos oficiais são a fonte da verdade visual.
2. Seletor de modelo/provedor funcional — começa no default do tenant e permite outros.
3. Instalação via CLI oficial — components.json (base base/rhea) + npx shadcn add + npm install.
4. Histórico completo — busca, agrupamento por data, renomear/excluir, ir para mensagem.
5. Entrega em 4 etapas: (1) infra shadcn, (2) backend override, (3) frontend thread+composer, (4) frontend histórico.

## 2. Contexto verificado
- ChatRequestDTO.java só aceita promptTemplateId/sessionId/message.
- ProviderResolution.java:64-68 sempre lê TenantLlmConfigService.require(tenantId); ordem load-bearing: modelo inválido → 400 antes de credencial; credencial ausente → 422.
- VendorCatalog tem modelsFor/defaultModelFor/validate/isSupportedModel.
- GET /v1/providers/{provider}/models já existe (ProviderModelsControllerV1.java:72); GET /v1/provider-credentials e GET/PUT /v1/tenant/llm-config também.
- chat_session sem colunas de provider/model; próxima migration livre do módulo agent é V7.

## 3. Decisão
- ChatRequestDTO e CreateSessionDTO ganham provider/model opcionais.
- ProviderResolution aceita override validado por VendorCatalog antes da credencial; ausente = default do tenant; parcial = 400.
- Escolha persistida em chat_session (V7, colunas nullable) e reutilizada via sessionId.
- ChatSessionDTO/ChatResponseDTO expõem provider/model efetivos.
- tenant_llm_config continua o default; sem FK cross-module.

## 4. Fora de escopo
AI SDK/@ai-sdk/react, tools/RAG/agentes, autenticação/MFA/menu, streaming WebSocket.

## 5. Riscos
Override parcial (validação antes da credencial), cliente reenviando override velho (sessionId autoritativo), sessões antigas (nullable → default), ordem Flyway (guard documentado).

## 6. Fontes (deepsearch 2026-11-20)
- https://ui.shadcn.com/docs/ai-elements/base/model-selector
- https://ui.shadcn.com/docs/ai-elements/base/chat
- https://ui.shadcn.com/docs/components/base/code-block
