# ARCHITECTURE — API pública do Assistant

**Data:** 2026-11-21 · **Fase:** Projeto (Fase 3 do dev-planner) · **Natureza:** modelo alvo; não autoriza implementação.

## 1. Fronteiras atuais (verificadas)

```
cliente ERP ──POST /v1/public/auth/token──▶ PublicAuthControllerV1
                                          │ PublicClientTokenIssuer (JWT HS256, aud=hive.public)
                                          ▼
cliente ERP ──POST /v1/public/v1/chat/completions──▶ PublicApiSecurityFilter
                                          │  JWT → authorities + TenantContext.runWithTenant
                                          ▼
                                   PublicChatControllerV1 (@HasPermission)
                                          ▼
                                   PublicChatServiceImpl
                                     ├─ RedisQuotaGuard.checkClient
                                     ├─ PromptGrantResolver.resolve (allowlist/pin)
                                     ├─ ProviderResolution.resolve  ─┐
                                     ├─ ChatTurnGateway.execute      │ reuso real da porta
                                     └─ TurnOutcomeMapper.resolve   ─┘
                                          ▼
                                   envelope OpenAI (Map) + metadata.hive

chain humana (/v1/**) ──SecurityFilter──▶ ChatControllerV1 ──▶ ChatServiceImpl
                                                          └─ mesma porta ChatTurnGateway
```

Grafo de módulos relevante: `publicapi/impl → agent/api + agent/impl`; `security` e `agent` são brothers. A aresta `publicapi/impl → agent/impl` existe hoje e é o débito K3.

## 2. Modelo alvo por frente

### 2.1 Contrato (ADR 1)

- **Request**: uma única classe em `publicapi/api/dto` — remover a duplicata de `web/internal/v1`. Campos canônicos OpenAI (`model`, `messages`, `stream`, `user`) + extensões Hive (`prompt_code`). Parâmetros aceitos-but-ignorados permanecem `@JsonIgnore` e passam a ter `@Schema` explicando o descarte.
- **Response**: DTOs em `publicapi/api/dto` — `ChatCompletionResponse`, `Choice`, `Message`, `Usage`, `HiveMetadata`, `OpenAiError`. `Map` desaparece do caminho público.
- **Erro**: manter handlers locais no controller; cada `@ExceptionHandler` ganha `@ApiResponse` com o código OpenAI correspondente. Nenhum advice global novo.
- **Spec**: `GroupedOpenApi` com group `public` e `pathsToMatch=/v1/public/**`; `springdoc.api-docs.path` e `springdoc.pathsToMatch` ficam configuráveis por environment. `securitySchemes` declara `bearerAuth` (JWT `hive.public`) e `clientCredentials` no token endpoint.
- **Exposição**: spec autenticada na chain humana por default; `/v3/**` deixa de ser `permitAll` se a decisão for confirmada. Scalar permanece em `/scalar`.

### 2.2 Centralização (ADR 2)

- Função pura em `agent/api` converte `role/content` do payload para `ChatMessage` do domínio, com as duas regras que hoje só o caminho humano aplica: descartar conteúdo nulo/branco e preservar a precedência do `SystemMessage` do template.
- `PublicChatServiceImpl` chama essa função; deixa de montar `ChatMessage` na mão.
- `TurnOutcomeMapper` passa a receber `turnId` e `clientId` explícitos e grava ambos em `ChatTrace` (K6).
- `ProviderResolution` e `TurnOutcomeMapper` **permanecem** em `agent/impl` neste PRD; a migração para `agent/api` fica no ADR 5.

### 2.3 Credenciais (ADR 3)

- Serviço de aplicação em `publicapi/impl` expõe operações de gestão: criar, listar, rotacionar, revogar, conceder/remover permissão, vincular prompt com pin.
- Superfície HTTP admin usa o padrão de painel já existente (`/v1/**`, chain humana), com `@HasPermission` de administração — **não** `/v1/public/**`.
- Frontend: view admin em `frontend/src/modules/hive/admin/`, consumindo service tipado; secret em plaintext apenas no create/rotate; nunca em listagens.
- Regras de domínio preservadas: `client_id` globalmente único; tenant da linha; rotação com janela `previous_secret_hash`; revogação imediata via Redis + DB. A UI não expõe limite por client: ele é do tenant (ADR 4).

### 2.4 Cota (ADR 4)

- `RedisQuotaGuard.checkTenant(tenantId, limit)` passa a ser o guard do caminho de chat: **um contador por tenant, compartilhado entre todos os clients do tenant**. Limite default `100` req/min, persistido em nova tabela de `publicapi_db` (migration V2, chave `tenant_id`), propagado no claim `plim.r` no issuance.
- `checkClient` permanece apenas no token endpoint, junto dos limites por IP e global (proteção contra força bruta, RFC 6749 §2.3.1). Não aplicar os 100 req/min ali.
- `public_api_client.rate_limit_per_minute` (default 60) fica obsoleto no caminho de chat; não remover a coluna sem ADR de limpeza, para não quebrar o contrato de dados atual.
- Staleness aceita: token com TTL de até 3600 s carrega o limite do tenant da emissão; alteração de limite só vale para tokens novos. Alternativa (leitura da tabela a cada turno) tem custo de DB por requisição e fica como decisão em aberto do ADR 4.
- Token endpoint troca 30/120/30 por constantes configuradas por perfil.
- Response de `429` mantém `Retry-After` e acrescenta `x-ratelimit-limit-requests`, `x-ratelimit-remaining-requests`, `x-ratelimit-reset-requests` quando o guard tiver os valores.
- Algoritmo ZSET + Lua, sufixo aleatório e fail-open permanecem. K12 (custo de ocupação por retry/read timeout) fica como débito de produto.

## 3. Fluxos

### 3.1 Emissão de token

1. Cliente envia `grant_type=client_credentials` + Basic (ou body, legado) para `/v1/public/auth/token`.
2. `resolveClientIp()` → `quotaGuard.checkTokenEndpoint(client, ip, …)`.
3. `PublicClientTokenIssuer.issueToken`: valida revoked/expired, verifica secret atual ou anterior, intersecta `scope` com grants, monta claims `pcode`/`plock`/`plim`.
4. Resposta `200` com `access_token`, `token_type=Bearer`, `expires_in`, `scope` e headers `Cache-Control: no-store`, `Pragma: no-cache`.

### 3.2 Turno de chat

1. `PublicApiSecurityFilter` valida JWT (issuer/audience/`typ`), consulta blacklist JTI e revogação, injeta authorities e abre `TenantContext`.
2. Controller valida `@Valid` e `@HasPermission("PUBLIC_CHAT_COMPLETE")`.
3. Serviço: rejeita `stream`/`system`; aplica limites `m/c/t`; `quotaGuard.checkTenant(tenantId, 100)` — contador compartilhado entre clients do tenant; allowlist de `prompt_code`; resolve prompt com pin; valida `model`; converte mensagens (função centralizada no ADR 2); executa `ChatTurnGateway`; mapeia falha; monta envelope tipado.
4. Erros viram `OpenAiErrorResponse` nos handlers locais; `429` leva `Retry-After` + headers de cota.

## 4. Sequenciamento de ADRs

| # | ADR | Depende de | Diretriz |
|---|---|---|---|
| 1 | Contrato tipado + OpenAPI da API pública | pré-condição springdoc | primeiro: destrava documentação e especificação |
| 2 | Centralizar conversão/system + trace | 1 (para contrato estável) | corrige K11/K6 |
| 3 | UI admin de credenciais | 1 (para contrato da API admin) | não expõe limite de requisições por client; cota é do tenant (ADR 4) |
| 4 | Cota por tenant e headers de rate limit | 1 | origem = `public_api_tenant_quota` em `publicapi_db`; paralelo ao ADR 3 |
| 5 | Fronteira `agent/api` × `agent/impl` (K3) | 2 | muda grafo; decisão de `architect` |

Teto de 5 ADRs por PRD atendido.

## 5. Evidência mínima exigida de cada implementação futura

- Spec `/v3/api-docs` com paths públicos e schemas não vazios; diff da spec revisado.
- ITs de caracterização: ordem do `SystemMessage`, `prompt_tokens`/`total_tokens`/`finish_reason`, formato de erro e headers de `429`.
- Suíte completa sem regressão; zero diff em `backend/security/**/Mfa*` e `identity/**/Mfa*`.
- diagnóstico de `model-not-found` idêntico entre chat humano e público.
- Teste com dois clients do mesmo tenant competindo pelo contador de 100 req/min: o segundo recebe `429` quando o somatório estoura; tenants diferentes não compartilham chave Redis.
- H3 quando houver mudança de UI admin: 375px e 1280px, console/network limpos.
