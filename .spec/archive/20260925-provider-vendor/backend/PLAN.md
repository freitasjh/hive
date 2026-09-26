# Implementation Plan: provider-vendor

## 1. Context & SPEC Traceability
- **Target Specification**: `.spec/backend/provider-vendor/SPEC.md` (ADR-009, catálogo MVP, V9, validate, credenciais por tenant)
- **Goal**: trocar provider-framework por vendor real (LangChain4j único), com credenciais por tenant, migração sem downtime e cadastro refeito — em 3 batches revisados.

### 1.1 SPEC Validation Report
- **Status**: Validated with Warnings
- **Identified Gaps/Warnings**:
  - Dono do módulo Flyway da `provider_credential` indefinido → **assunção**: `agent/dbupdate` (tabela pertence ao contexto agent; history `flyway_schema_history_agent`; migration `V9__...` na pasta `agent/`). Se `agent/dbupdate` estiver em V8, V9 é a próxima.
  - Limites de throttling do `validate` indefinidos → **assunção**: 10 req/min por tenant (in-memory, `ValidateRateLimiter`), documentado; sem nova dependência.
  - `XAI` fora do MVP → `OPENAI_COMPATIBLE` cobre via baseUrl; sem enum XAI.
- **Assumptions Made**: colunas `provider_type`/`llm_model` reaproveitadas (sem DDL); `OPENAI_COMPATIBLE` não ofertado na UI de criação; `GET credentials` nunca expõe ciphertext.

## 2. Technical Strategy
### 2.1 Backend Strategy
- **Framework**: Spring Boot 4.1.0, Java 21; LangChain4j 1.19.0 único runtime LLM.
- **Architectural Pattern**: Monólito modular DDD (`api → impl → dbupdate → web/internal/v1`); MapStruct obrigatório; `JpaRepository` direto.
### 2.2 Frontend Strategy
- **Framework**: Vue 3.5 + PrimeVue 4; Composition API; axios via `api.ts`; specs Vitest + jsdom.
### 2.3 Infrastructure Strategy
- **Deployment**: release único BE+FE (breaking); janela de migração V9 + rollback (restore por `id`).
- **CI/CD**: `mvn clean install` + `mvn verify -pl integration-test` + `npm run test` + `npm run build` por batch.

## 3. Architecture Realization & Constraint Mapping
| SPEC Reference | Constraint | Enforcement Point | Failure Mode | Implementation Strategy |
|---|---|---|---|---|
| ADR-009 §3 | Vendor enum, sem framework | `provider/api`, factory, DTOs | 502/contract drift | Novo `AiProvider`; remover `AiProviderType`; grep final zero refs |
| SPEC §4 | AES-256-GCM + máscara | `AttributeConverter`, DTO GET | Vazamento de chave | Converter + DTO `{configured,last4}` + IT anti-exposição |
| SPEC §4 | SSRF baseUrl | `ValidateService`/credential PUT | SSRF rede interna | Allowlist + bloqueio IP privado/RFC1918 + testes |
| SPEC §4 | Tenant isolation | `CredentialService` (filtro tenant) | IDOR cross-tenant | `tenantId` do JWT em toda query + IT cross-tenant |
| SPEC §7 | Catálogo MVP | `AgentService` (BE autoridade) | Modelo inválido | `Map<AiProvider,List<String>>` + 400 `MODEL_NOT_SUPPORTED_FOR_PROVIDER` |
| SPEC §5 | V9 dados | Flyway `agent` | Agentes legados quebrados | `UPDATE` por `llm_model` + audit prévio + IT em PG real |

## 4. Module & Code Structure
| Module Name | Responsibilities | Boundaries & Communication |
|---|---|---|
| `provider/api` | `AiProvider`, `LlmClientFactory` (nova assinatura), `ChatRequestProvider.model:String` | Sem deps de impl; consumido por `agent` |
| `provider/langchain4j` | `LangChain4jVendorRouter` + builders por vendor, `CredentialResolver` | Lê `provider_credential` via `agent` api? **Não** — resolver vive em `agent/impl` (evita `provider→agent`); factory recebe `apiKey/baseUrl` resolvidos |
| `agent/*` | `ProviderCredentialEntity/repo`, `CredentialService/Controller`, `ValidateService/Controller`, `Agent` remodelado, V9 | Dono Flyway `agent` |
| `provider/spring-ai` | **REMOVIDO** | Remover de todos os poms + factory + FE |
| `frontend/admin` | `CredentialsView`, `ProviderSelect`, `ConnectionTester`, `AgentEditorView` | Via `agent-admin.service.ts` |

## 5. Risks & Complexity Analysis
- **🔥 CRITICAL: agentes legados sem mapeamento** → Mitigação: audit counts (INFRA-02) antes; V9 idempotente; IT com seed legado.
- **🔥 CRITICAL: chave ausente em runtime** → Mitigação: 422 `CONFIG_Missing` explícito + badge `needsKey` na UI.
- **HIGH: SSRF** → allowlist + testes (BE-06).
- **MED: release parcial** → release único BE+FE; specs FE atualizadas no mesmo batch.

## 6. Implementation Phases & Dependencies
### Batch 1 — Fundação backend (módulos: `provider`, `agent` base, `dependencies`)
- **Dependencies**: INFRA-02 (audit) antes de BE-07 (V9).
- **Scope**: BOM deps, enum+catálogo, credential entity/resolver/CRUD, ITs base. Sem breaking em `Agent` ainda.
- **Explicitamente ADIADO p/ Batch 2 (BE-05)**: integração `ChatServiceImpl ↔ CredentialResolver` (chat flow continua no padrão legado neste batch — decisão de sequenciamento, não falha de entrega).
### Batch 2 — Vendor no Agent + validate + remoção spring-ai
- **Dependencies**: Batch 1.
- **Scope**: factory por vendor, `Agent` remodelado, `POST /validate` (+SSRF+throttle), V9, remoção total spring-ai.
### Batch 3 — Frontend
- **Dependencies**: Batch 2 (contratos).
- **Scope**: CredentialsView, ProviderSelect, ConnectionTester, AgentEditorView, specs. Harness E2E ao final.

## 7. Critical Execution Path
INFRA-01 → BE-01 → BE-02 → BE-03 → BE-04 → BE-05 → BE-07 → BE-08 → FE-02 → FE-03 → harness

## 8. Atomic Task Breakdown & Execution Graph

### Batch 1
- **[INFRA-01]** [ ] Adicionar deps LangChain4j Gemini + Anthropic ao BOM
  - **Depends on**: None · **Layer**: Infra · **Artifact**: `backend/dependencies/pom.xml` (`langchain4j-google-ai-gemini`, `langchain4j-anthropic` 1.19.0) · **Enforcement**: build resolve artifacts · **Constraint Ref**: SPEC §7 · **Framework Detail**: Maven BOM
- **[INFRA-02]** [ ] Auditar dados legados de provider/model
  - **Depends on**: None · **Layer**: Infra · **Artifact**: evidência `SELECT provider_type, llm_model, count(*) FROM agent` (dev; prod se acesso) · **Enforcement**: V9 só com tabela-verdade fechada · **Constraint Ref**: SPEC §5
- **[BE-01]** [ ] Novo enum `AiProvider` + catálogo por vendor
  - **Depends on**: None · **Layer**: Domain (`provider/api`) · **Artifact**: `AiProvider.java`, `VendorCatalog.java` (+default por vendor) · **Enforcement**: 400 fora do catálogo · **Constraint Ref**: ADR-009 §2
- **[BE-02]** [ ] `ProviderCredentialEntity` + repo + converter AES + `CredentialResolver`
  - **Depends on**: BE-01 · **Layer**: Data/Domain (`agent/impl`, api) · **Artifact**: entity/repo/`AesGcmAttributeConverter`/`CredentialResolver` (tenant→credential→env→422) · **Enforcement**: AES-256-GCM, IV random, `key_version` · **Constraint Ref**: SPEC §4
- **[BE-03]** [ ] CRUD mascarado de credenciais + ITs
  - **Depends on**: BE-02 · **Layer**: Interface+Integration · **Artifact**: `CredentialControllerV1`, DTOs mascarados, `CredentialIT` (máscara, IDOR cross-tenant, prefixo 400, PUT/DELETE audit) · **Enforcement**: `@HasPermission(AGENT_MANAGE)`, `@Valid` · **Constraint Ref**: SPEC §3/§6.4

### Batch 2
- **[BE-04]** [ ] `LangChain4jClientFactory` por vendor + unitários (NOTA review S1: desabilitar auto-redirect HTTP ou re-validar URL pós-redirect — SSRF via 3xx)
  - **Depends on**: INFRA-01, BE-01 · **Layer**: Domain (`provider/langchain4j`) · **Artifact**: builders OPENAI/OLLAMA/GEMINI/ANTHROPIC/OPENAI_COMPATIBLE(+baseUrl) · **Enforcement**: vendor sem artifact não compila · **Constraint Ref**: SPEC §6.3
- **[BE-05]** [ ] Remodelar `Agent` (provider+model) + `ChatServiceImpl` via resolver
  - **Depends on**: BE-01, BE-02, BE-04 · **Layer**: Domain/App · **Artifact**: `Agent`, `AgentEntity`, DTOs, mappers, `ChatServiceImpl`, `LlmClientFactory` nova assinatura · **Enforcement**: catálogo BE autoridade; `ChatRequestProvider.model:String` · **Constraint Ref**: SPEC §6.2
- **[BE-06]** [ ] `POST /v1/agents/validate` (+SSRF+throttle) + ITs
  - **Depends on**: BE-04, BE-05 · **Layer**: Interface+Integration · **Artifact**: `ValidateService/ControllerV1`, `ValidateRateLimiter` (10/min/tenant), SSRF guard, `ValidateIT` (ok:false sem persistir, 401 caller, timeout, ordenação de rota) · **Enforcement**: sem `apiKey` no body; envelope `200+ok:false`; timeout 5s executor · **Constraint Ref**: SPEC §4/§6.4
- **[BE-07]** [ ] Flyway V9 migração de dados + IT
  - **Depends on**: INFRA-02, BE-05 · **Layer**: Data · **Artifact**: `V9__migrate_provider_to_vendor.sql` (mapeamento §5) + IT em PG real com seed legado · **Enforcement**: idempotente; zero perda de params · **Constraint Ref**: SPEC §5
- **[BE-08]** [ ] Remover `provider/spring-ai` por completo
  - **Depends on**: BE-05 · **Layer**: Infra/Domain · **Artifact**: remoção poms (`provider/`, `dependencies/`, `bootstrap/`, `agent/impl/`), factory, `AgentDataFactory`/`ChatServiceImplTest`/`DtoValidation`/`AgentControllerV1Test`, grep zero `SPRING_AI|AiProviderType|LLMModel` · **Enforcement**: build verde sem o módulo · **Constraint Ref**: ADR-009 §2

### Batch 3
- **[FE-01]** [ ] Types + service (validate/credentials) + specs
  - **Depends on**: BE-03, BE-06 (contratos) · **Layer**: Frontend · **Artifact**: `types/agent-admin.ts` (novo enum), `agent-admin.service.ts`, specs · **Enforcement**: tipos espelham DTOs · **Constraint Ref**: SPEC §8
- **[FE-02]** [ ] `ProviderSelect` + modelo combobox + refactor seção `AgentEditorView`
  - **Depends on**: FE-01 · **Layer**: Frontend · **Artifact**: `ProviderSelect.vue`, `ConnectionTester.vue`, seção "Provedor de IA" (badge chave, sem apiKey) · **Enforcement**: UX-SPEC §1-5; PT-BR; aria · **Constraint Ref**: UX-SPEC
- **[FE-03]** [ ] `CredentialsView` + rota + specs
  - **Depends on**: FE-01 · **Layer**: Frontend · **Artifact**: `CredentialsView.vue` (`/settings/providers`), diálogo upsert, `AdminViews.spec`/`AgentEditorDialog.spec` atualizados · **Enforcement**: UX-SPEC §6; máscara `sk-••••`; DELETE com confirmação · **Constraint Ref**: UX-SPEC
- **[FE-04]** [ ] Harness E2E provider-vendor
  - **Depends on**: FE-02, FE-03 · **Layer**: Frontend · **Artifact**: evidência (cadastro→teste→chat <3min, screenshots, console/network limpos) · **Enforcement**: H1-H5 harness · **Constraint Ref**: CA3

## 9. Event Implementation Mapping
N/A justificado — refactor sem eventos/mensageria (sem producer/outbox/broker/consumer).

## 10. Execution Readiness Checklist
- [x] Dependências válidas, sem ciclos (Batch1 → Batch2 → Batch3; INFRA-02 → BE-07).
- [x] Sem eventos na SPEC (N/A justificado).
- [x] Testes mandatórios: IDOR credential (BE-03), tenant isolation, V9 PG real (BE-07), anti-exposição (BE-03), 401/403 (BE-03/BE-06).
- [x] Infra bootstrap: BOM (INFRA-01), audit (INFRA-02), Testcontainers existente.
- [x] Critical path mapeia o fluxo core (credencial → vendor → validate → migração → UI → harness).
