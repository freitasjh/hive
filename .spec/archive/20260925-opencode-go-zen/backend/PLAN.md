# Implementation Plan: opencode-go-zen

## 1. Context & SPEC Traceability
- **Target**: `.spec/backend/opencode-go-zen/SPEC.md` (ADR-011, R1-R12, CA1-CA4)
- **Goal**: enums GO/ZEN + custom first-class + discovery filtrado em 2 batches.

### 1.1 Validation
- **Status**: Validated with Warnings (defaults GO/Zen vs lista viva → confirmar na Fase 2 contra `/v1/models`; `mimo-v2.5`/`muse-spark-1.3` assumidos).

## 2. Strategy
- Backend: Spring Boot 4.1/Java 21, LangChain4j `OpenAiChatModel` + baseUrl + headers; zero DDL.
- Frontend: Vue 3.5 + PrimeVue 4; `useRemoteModels`; axios via `api.ts`.

## 3. Constraint Mapping
| SPEC | Constraint | Enforcement | Failure | Strategy |
|---|---|---|---|---|
| R1 | Discovery pinado | `KbPinnedHttpClient` pattern + sem redirect + 512KB | SSRF/TOCTOU | Reusar client pinado; novo serviço |
| R2 | Custom endurecido | CGNAT block + https não-loopback + pin no call | SSRF/custo | Estender `ProviderBaseUrlGuard` + factory pin |
| R3 | LGPD | Banners editor/dialog/KB | Compliance | FE (Batch 2) |
| R4-R6 | Sem fallback + evict | 422 `MODEL_UNAVAILABLE` + evict cache | Custo surpresa | Chat + discovery |
| R5 | Rate limit chat + log uso | Novo limiter + tokens logados | Fatura aberta | `ChatServiceImpl` |
| R8-R12 | key_version, prefix skip, charset, headers | Código + testes | Divergência | Units |

## 4. Modules
| Module | Resp | Boundaries |
|---|---|---|
| `provider/api` | Enum + catalog + denylist + policy | Sem rede |
| `provider/langchain4j` | Discovery service + factory (baseUrl/headers) + UA interceptor | Rede isolada aqui |
| `agent` | Credentials CRUD (existe) + validate estendido + rate limit chat + key_version | Consome provider |
| `frontend` | Select, editor, credenciais, composable | Via REST |

## 5. Risks
- **🔥 Zen sem teto** → rate limit + log (caps fase 2).
- **HIGH lista instável** → badge + 422 + refresh.
- **MED SSRF custom** → R2 + ITs.

## 6. Phases
### Batch 1 — Backend
INFRA-01 (confirmar defaults contra `/v1/models` vivos) → BE-01 (enum+catalog+policy/resolver) → BE-02 (factory baseUrl/headers/UA) → BE-03 (discovery + endpoint + evict) → BE-04 (validate/custom guards/key_version/rate-limit-chat/log) → BE-05 (ITs: discovery/IDOR/SSRF/stale).
### Batch 2 — Frontend + DOC
FE-01 (types/service) → FE-02 (select+metadata) → FE-03 (editor remoto+LGPD+badge) → FE-04 (credenciais GO/ZEN/custom) → FE-05 (harness GO+custom) → DOC-03 (SPEC §4.3 + ADR-011 Aceito).

## 7. Critical Path
INFRA-01 → BE-01 → BE-02 → BE-03 → BE-04 → BE-05 → FE-02 → FE-03 → harness

## 8. Tasks (resumo; BDD/TDD/DoD no TASKS.md)
- **[INFRA-01]** Confirmar defaults + denylist contra listas vivas GO/Zen (evidência).
- **[BE-01]** `AiProvider.OPENCODE_GO/ZEN` + `VendorCatalog` (presets/denylist/defaults) + `CredentialPolicy` (skip prefix) + `CredentialResolver` (env names) + units.
- **[BE-02]** Factory: branches GO/ZEN (OpenAi + baseUrl fixa + `customHeaders` session + UA interceptor `hive/x.y` só `opencode.ai`) + units.
- **[BE-03]** `OpencodeDiscoveryService` (pinned, 5s, 512KB, schema, denylist) + `GET /v1/providers/{go,zen}/models` (auth + rate-limit + cache 10min + evict) + units.
- **[BE-04]** Custom first-class (`hiddenFromCreate` BE? — remover trava; CGNAT/https/pin) + validate mandatório GO/ZEN/custom + rate limit chat + log tokens + `key_version` + 422 `MODEL_UNAVAILABLE` + units.
- **[BE-05]** ITs (discovery mock/WireMock-like, IDOR, SSRF custom, stale 422, sem regressão).
- **[FE-01]** Types/service/specs. **[FE-02]** Select + metadata + VENDOR_ORDER. **[FE-03]** Editor remoto + LGPD + badge + credenciais. **[FE-04]** Harness GO+custom. **[DOC-03]** Delta docs.

## 9. Events
N/A justificado.

## 10. Readiness
- [x] Sem ciclos; infra-first; critical path; testes mandatórios (IDOR/SSRF/stale); Testcontainers.
