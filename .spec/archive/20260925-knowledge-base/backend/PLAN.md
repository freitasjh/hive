# Implementation Plan: knowledge-base

## 1. Context & SPEC Traceability
- **Target Specification**: `.spec/backend/knowledge-base/SPEC.md` (ADR-010, R1-R18, CA1-CA6, RN-KB1-RN-KB7)
- **Goal**: módulo `knowledge/` com RAG tenant-isolado em 2 batches + DOC.

### 1.1 SPEC Validation Report
- **Status**: Validated with Warnings
- **Gaps/Warnings**: local de seed das permissões `KNOWLEDGE_*` a confirmar na Fase 2 (assunção: migration de seed no módulo `knowledge/dbupdate` + registro em `SecurityConstants` + lista do token); porta do playground `.../search` (assunção mantida).
  - **DECISÃO F1 (travada)**: seed em migration `knowledge/dbupdate` + `SecurityConstants` + lista do token — implementar em BE-04 antes de qualquer FE.
- **Assumptions**: tabelas embeddings iniciais `_1536` + `_768`; chunking 512/50; `@Async` MVP.

## 2. Technical Strategy
### 2.1 Backend Strategy
- Spring Boot 4.1.0/Java 21; LangChain4j 1.19.0 (`pgvector`, `document-parser-apache-tika`); retrieval manual; Flyway próprio.
### 2.2 Frontend Strategy
- Vue 3.5 + PrimeVue 4 (FileUpload, AutoComplete, Slider, Tag, Skeleton); axios via `api.ts`.
### 2.3 Infrastructure Strategy
- Release único BE+FE; V11/V12 + migração `TEXT[]`; Testcontainers PG16+pgvector p/ ITs.

## 3. Constraint Mapping
| SPEC | Constraint | Enforcement | Failure Mode | Strategy |
|---|---|---|---|---|
| R1-R4 | SSRF ingestão | Guard dedicado + redirect NEVER + pin IP | SSRF rede interna | `KbUrlGuard` + ITs loopback/redirect/metadata |
| R5-R9 | Tenant isolation | `tenant_id NOT NULL` + `WHERE` explícito + `runWithTenant` | Cross-tenant leak | Filtro em toda query; IT IDOR; vazio=zero retrieval |
| R11-R13 | Upload seguro | multipart 10MB/12MB + magic-bytes + UUID storage | Tika bomb/traversal | Props dev+prod + IT 413/400 |
| R14 | Embedding tenant | `CredentialResolver` (sem env global) | Custo cruzado | Resolver por `(tenant, provider)` |
| RN-KB3 | Limites | Validação service (10/50/10MB) | Abuso/custo | 400/413 + IT |
| RN-KB5/6 | Vínculo N:N | Junção + max 5 + 409 `KB_IN_USE` | Chat quebrado | FK CASCADE + force flag |

## 4. Modules
| Module | Responsibilities | Boundaries |
|---|---|---|
| `knowledge/api` | VOs, `KbRetrievalService`, exceptions | Sem impl; consumido por `agent` |
| `knowledge/impl` | Entities, repos, ingest (`@Async`), retrieval, guards | Só via `api` p/ `agent` |
| `knowledge/dbupdate` | V11/V12 + extension + seeds | History própria |
| `knowledge/web` | Controllers, DTOs, mappers | DTOs mascarados |
| `agent` | `ChatServiceImpl` retrieval + vínculo | Consome `knowledge/api` |
| `frontend` | Views/service/types/menu | Via REST |

## 5. Risks
- **🔥 Cross-tenant leak** → R5-R9 + IT IDOR obrigatórios.
- **🔥 SSRF** → guard + ITs (gate).
- **HIGH custo** → limites + dedup + token log.
- **MED migração TEXT[]** → backfill + leitura legada 1 release.

## 6. Phases
### Batch 1 — Backend (knowledge/ + chat wiring)
- Deps: INFRA-02 (audit `TEXT[]` existentes) antes de BE-07.
- Scope: BOM+props, entities+V11, ingestão, V12+store+retrieval+chat, CRUD+vínculo+permissions, ITs.
### Batch 2 — Frontend + harness
- Scope: service/types/menu/rotas, List/Detail/playground/picker, specs, harness E2E.

## 7. Critical Execution Path
INFRA-01 → BE-01 → BE-02 → BE-03 → BE-04 → BE-05 → BE-07 → FE-03 → harness (BE-06 compat em paralelo pós-BE-05; FE-03 aguarda BE-06+BE-07)

## 8. Atomic Tasks
### Batch 1
- **[INFRA-01]** Deps BOM (`langchain4j-pgvector`, `document-parser-apache-tika` 1.19.0) + multipart props (dev+prod) — Layer Infra.
- **[INFRA-02]** Audit `knowledgeBases TEXT[]` existentes (tabela-verdade p/ BE-07).
- **[BE-01]** Entities (`KnowledgeBase`, `KbDocument`, `AgentKb`) + repos + **V11** (extension + tabelas + índices) + units.
- **[BE-02]** Ingestão: upload (magic-bytes, UUID storage) + URL (`KbUrlGuard` dedicado: loopback/CGNAT/redirect/IP-pin/porta/timeout/2MB) + `@Async` executor + `runWithTenant` + status + units.
- **[BE-03]** `V12` (tabelas `_1536`/`_768`, JSONB, índices) + store (`createTable=false`, `datasourceBuilder`) + `KbRetrievalService` (tenant+kb_ids server-side, maxResults 5, minScore 0.7, timeout 2s) + `ChatServiceImpl` wiring (`rag_used`/citations) + `POST .../search` + units.
- **[BE-04]** CRUD KB/documentos + vínculo N:N (max 5) + delete 409/force + `KNOWLEDGE_*` permissions + reindex idempotente.
- **[BE-05]** ITs: `KnowledgeBaseIT` (CRUD+search+playground), `KnowledgeIsolationIT` (cross-tenant retrieval+CRUD), `KnowledgeSsrfIT` (loopback/redirect/metadata), `KnowledgeMigrationIT` (`TEXT[]` backfill + V11/V12 em PG real).
- **[BE-06]** Remover `knowledgeBases` string[] do contrato? **NÃO** — leitura legada 1 release (PM): manter campo + popular a partir da junção; escrita via vínculo.
- **[BE-07]** Migração backfill `TEXT[]`→KBs+jUNÇÃO (parte da V11? migration de dados separada **V13** se V12 for DDL; decidir na implementação: DDL V11/V12, dados V13).

### Batch 2
- **[FE-01]** `types/knowledge.ts` + `services/knowledge.service.ts` + specs.
- **[FE-02]** `KnowledgeBaseList.vue` + `KnowledgeBaseDetail.vue` (tabs, upload, docs, reindex) + rotas + menu + breadcrumb + specs.
- **[FE-03]** Playground tab + picker no `AgentEditorView` (max 5 + contador) + specs.
- **[FE-04]** Harness E2E (upload→index→pergunta citada + cross-check desvinculada; 1280+375; PT-BR).
- **[DOC-02]** Delta: seção Knowledge em `SYSTEM_SPECIFICATION.md` + `AGENTS.md` + fechar ADR-010.

## 9. Events
N/A justificado — async local sem mensageria (RabbitMQ fase 2).

## 10. Readiness
- [x] Dependências sem ciclos; infra-first; critical path core; testes mandatórios (IDOR, SSRF, migração); Testcontainers PG16+pgvector.
