# JIRA BOARD — EPIC provider-vendor

> Classificação: **EPIC** (refactor) · Fonte: `PLAN.md` + `TASKS.md` · Isolamento BE/FE estrito.

## Batch 1 — Fundação backend
| Key | Task (TASKS.md) | Escopo | Status |
|---|---|---|---|
| PV-INFRA-01 | INFRA-01 | `backend/dependencies/pom.xml` (BOM gemini+anthropic) | Done |
| PV-INFRA-02 | INFRA-02 | audit `SELECT provider_type, llm_model, count(*)` | Done |
| PV-BE-01 | BE-01 | `provider/api`: `AiProvider.java`, `VendorCatalog.java` + teste | Done |
| PV-BE-02 | BE-02 | `agent`: `ProviderCredentialEntity`, repo, `AesGcmAttributeConverter`, `CredentialResolver` + testes | Done |
| PV-BE-03 | BE-03 | `CredentialControllerV1` + DTOs + `CredentialIT` | Done |

## Batch 2 — Vendor no Agent
| Key | Task (TASKS.md) | Escopo | Status |
|---|---|---|---|
| PV-BE-04 | BE-04 | `LangChain4jClientFactory` por vendor + teste | Done |
| PV-BE-05 | BE-05 | `Agent`/entity/DTOs/mappers + `ChatServiceImpl` + ponte CredentialResolver (Batch1→2) + testes | Done |
| PV-BE-06 | BE-06 | `ValidateService/ControllerV1` + throttle + SSRF + `ValidateIT` | Done |
| PV-BE-07 | BE-07 | `V9__migrate_provider_to_vendor.sql` + IT PG real | Done |
| PV-BE-08 | BE-08 | Remoção `provider/spring-ai` + testes afetados | Done |

## Batch 3 — Frontend
| Key | Task (TASKS.md) | Escopo | Status |
|---|---|---|---|
| PV-FE-01 | FE-01 | `types/agent-admin.ts`, `agent-admin.service.ts` + specs | Done |
| PV-FE-02 | FE-02 | `ProviderSelect.vue`, `ConnectionTester.vue`, seção editor | Done |
| PV-FE-03 | FE-03 | `CredentialsView.vue`, rota `/settings/providers` | Done |
| PV-VAL-01 | FE-04 | Harness E2E 4 vendors (Anexo B) | Done |
| PV-DOC-01 | DOC-01 | Delta §4.2 + AGENTS.md + ADR-009 | Todo |

**Regra**: 1 microtask por loop de execução; atualizar Status (Todo → Doing → Done) a cada conclusão.
