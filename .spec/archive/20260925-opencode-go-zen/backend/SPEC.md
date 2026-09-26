# SPEC — OpenCode GO/Zen + Provider Custom

> **ID**: `opencode-go-zen` · **Feature** · **Status**: `Under Review`
> **Fonte**: PROPOSAL + triagem + ux-designer (vinculante) + security-engineer (R1-R12) + decisão B (enums)

## 1. ADRs
- **ADR-011** (`docs/adr/ADR-011-Opencode-Go-Zen.md`, a criar): enums GO/ZEN; discovery manual filtrado; custom first-class; headers; sem fallback; caps fase 2.
- **ADR-009** (herdado): vendor real; credencial por tenant; `OPENAI_COMPATIBLE` genérico.

## 2. Domínio
`provider/` (enum, catalog, discovery, factory, headers) + `agent/` (credentials CRUD existentes, validate estendido, chat 422) + `frontend` (select, editor, credenciais). Zero DDL (VARCHAR(32) sem CHECK — confirmado).

## 3. IAM/RBAC
- `GET /v1/providers/{go,zen}/models`: `@HasPermission(AGENT_MANAGE)` + rate limit (reusar `ValidateRateLimiter`).
- Demais endpoints existentes inalterados.

## 4. Contratos
### 4.1 Enum + catálogo
- `AiProvider += OPENCODE_GO, OPENCODE_ZEN`. `VendorCatalog`: presets (baseUrl fixa, defaults: GO `mimo-v2.5`, Zen **`claude-sonnet-4-5`** — INFRA-01 confirmou `muse-spark-1.3-contributor` morto e denylisted; ver comentário em `VendorCatalog.java`), denylist `RESPONSES={grok-*,gpt-*-luna,muse-spark-*}`, `MESSAGES={minimax-*,qwen*}` (patterns versionados em código; fase 2: tabela admin se churn >1x/trimestre).
- `OPENAI_COMPATIBLE`: `hiddenFromCreate=false`, label "Custom (endpoint próprio)".
- `CredentialPolicy`: GO/ZEN sem validação de prefixo (`assertPrefix→true`); `CredentialResolver` + envs `OPENCODE_GO_API_KEY`/`OPENCODE_ZEN_API_KEY` (preferir credencial tenant; env só single-tenant).
- Ripple: `supports/validate`, factory branches (OpenAi + baseUrl), `VENDOR_CATALOG`/`VENDOR_ORDER` FE.
### 4.2 Discovery
- `OpencodeDiscoveryService` (em `provider/langchain4j`): `GET {base}/v1/models` via **client pinado** (padrão `KbPinnedHttpClient`): sem redirect, timeout 5s, maxBytes 512KB, schema-validate (`[A-Za-z0-9._:-]{1,128}`, cap ~300).
- `GET /v1/providers/{go,zen}/models` → `200 {models:[{id}], filteredOut:[{id,reason}], cachedAt}`; falha → 422 `DISCOVERY_FAILED`; baseUrl sempre constante do vendor (nunca do tenant).
- Cache in-memory 10min por vendor + refresh manual; **evict em 422/404 model-not-found**; mapear p/ 422 `MODEL_UNAVAILABLE` + hint refresh.
### 4.3 Custom
- baseUrl editável + chave; guard estendido: **CGNAT 100.64/10 bloqueado**, **https obrigatório p/ não-loopback**, pin de IP no call do chat; loopback permitido (exceção documentada; verificar actuator prod).
- Teste de conexão **mandatório** p/ GO/ZEN/custom (fail-closed: agente só ativa após `ok`).
### 4.4 Headers Go
- `x-opencode-session` = session UUID4 **server-set**, só GO/Zen, nunca logado.
- `User-Agent: hive/x.y` via interceptor **após** `customHeaders` (não sobrescrevível); só p/ host `opencode.ai`.
### 4.5 Chat/custo
- Model removido → 422 `MODEL_UNAVAILABLE` (sem fallback); userMsg preservada.
- **Rate limit de chat por tenant (NOVO)** + **log de uso (tokens)** desde o dia 1; teto mensal + alertas = fase 2.
- `key_version` incrementa na rotação; documentar rotação master key (re-save).
### 4.6 LGPD (R3 — bloqueante MVP)
- Banner `Message info` no editor (GO/ZEN selecionado) + dialog de credencial + nota em KB vinculada a agente externo: "prompts/dados trafegam pela API OpenCode".

## 5. Frontend (vinculante — detalhe `.spec/frontend/opencode-go-zen/UX-SPEC.md`)
`VENDOR_ORDER` 7 itens; metadata (`costHint`, `readOnlyBaseUrl`, `remoteModels`, `modelsEndpoint`, `lgpdWarning`); `useRemoteModels` (loading/error/stale 5min + refresh); dropdown remoto + badge stale; CredentialsView GO/ZEN/custom; badge "fora do catálogo" não-bloqueante; aria-live; PT-BR; dark.

## 6. Invariantes
| Entidade | Propriedade |
|---|---|
| credential | `(tenant, provider)` única; GET nunca expõe valor |
| discovery | nada persiste; cache 10min; evict em MODEL_UNAVAILABLE |
| chat | sem fallback silencioso; 422 legível; userMsg preservada |
| custom | https não-loopback; pin no call |

## 7. RF/RN/CA
- **RF1**: agentes GO/ZEN/custom respondem via `chat/completions`. **RF2**: discovery filtrado + refresh. **RF3**: custom com baseUrl+chave próprios.
- **RN1**: teste mandatório GO/ZEN/custom. **RN2**: sem fallback. **RN3**: LGPD visível. **RN4**: rate limit chat + log uso.
- **CA1**: GO responde 200. **CA2**: custom responde 200. **CA3**: removido → 422 legível, zero 500. **CA4**: descoberta ≥1 filtrado.

## 8. Security Assessment — Fase 1
STRIDE/auditoria no relatório `security-engineer` (R1-R12). **Verdict: APROVADO com condições** — 3 🔴 vinculantes (R1 pinned fetch, R2 custom CGNAT+https+pin, R3 LGPD) + R4-R12 como tasks. Zero 🔴 solto se implementados.

## 9. Testes
- **Units**: catalog (GO/ZEN/denylist), discovery (mock HTTP: filtro, cap, charset, evict), headers (session server-set, UA), `key_version`, rate limit chat.
- **ITs**: discovery 200/422 + auth/rate-limit; IDOR credencial GO/ZEN; SSRF custom (loopback permitido, CGNAT/https/porta); stale → 422; sem regressão (`CredentialIT` etc.).
- **FE**: `useRemoteModels`, dropdown remoto, badge stale, LGPD, VENDOR_ORDER; `npm run test` verde.
- Cobertura padrão (80/90/70).

## 10. Migração
Zero DDL. Credenciais GO/ZEN = novas linhas `(tenant, vendor)` via tela existente. Agentes existentes intactos.

## 11. Delta Specs
### Delta: `docs/product/SYSTEM_SPECIFICATION.md` — MODIFIED (§4.3: GO/ZEN/custom/discovery/headers/LGPD/caps-fase-2)
### Delta: `docs/adr/` — ADDED (`ADR-011-Opencode-Go-Zen.md`)
### Resumo: SYSTEM_SPEC 0/1/0 · adr 1/0/0 · AGENTS.md 0/0/0
