# SPEC — Base de Conhecimento / RAG (pgvector, módulo `knowledge/`)

> **ID**: `knowledge-base` · **Tipo**: Feature · **Status**: `Under Review`
> **Fonte**: PROPOSAL + triagem (architect/PM) + ux-designer (vinculante) + security-engineer (18 requisitos R1-R18) + estudo Brain + decisão usuário (módulo separado)

---

## 1. ADRs

- **ADR-010** (`docs/adr/ADR-010-KB-RAG-pgvector-manual.md`, a criar): retrieval manual; tabela-por-dimensão; Flyway próprio (`createTable=false`); módulo `knowledge/`; OpenAI-1536 default + Ollama-768; `@Async` MVP.
- **ADR-009** (herdado): LangChain4j único; credencial por tenant (reutilizada p/ embeddings — sem env global compartilhada).
- Tenant strategy (herdada): row-level isolation (`AbstractEntity` + `@Filter`).

## 2. Contexto de Domínio

- **Bounded context**: `knowledge/` (`api/impl/dbupdate/web/internal/v1`) — KBs, documentos, chunks/embeddings, vínculo agente↔KB, playground. `agent/` consome via `knowledge/api` (retrieval no `ChatServiceImpl`).
- **Flyway**: history `flyway_schema_history_knowledge`, location `knowledge/dbupdate/.../migration/knowledge/`. `V11__rag_kb_core.sql` (extension + `knowledge_base` + `kb_document` + junção) + `V12__rag_embeddings.sql` (tabelas por dimensão + índices).

## 3. IAM / RBAC

| Endpoint | Permissão |
|---|---|
| `POST/PUT/DELETE /v1/knowledge-bases*`, upload, URL, reindex | `KNOWLEDGE_MANAGE` |
| `GET /v1/knowledge-bases*`, `POST .../search` | `anyOf={KNOWLEDGE_READ, KNOWLEDGE_MANAGE}` |
| Chat com RAG | via agente (`CHAT_SEND` + vínculo server-side; `KNOWLEDGE_READ` sozinho NÃO autoriza retrieval arbitrário) |
- `KNOWLEDGE_MANAGE`/`KNOWLEDGE_READ` em `SecurityConstants` + lista do token + migration de seed (local a confirmar na Fase 2). Cross-tenant → 404 uniforme (anti-IDOR).

## 4. Segurança (requisitos vinculantes R1-R18 — auditoria security-engineer)

- **SSRF (R1-R4)**: guard dedicado ingestão (loopback + CGNAT 100.64/10 + docs ranges bloqueados); `followRedirects(NEVER)` + revalidação ≤3 hops ou rejeitar; anti-rebinding (conectar IP validado/pinned); portas 80/443; timeouts connect 5s/total 10s; limite 2MB via stream; content-type + magic-bytes; fail-closed uniforme.
- **Tenant (R5-R9)**: `tenant_id UUID NOT NULL` + índices; `WHERE tenant_id` explícito em TODA query vetorial (nativa não herda `@Filter`) — fail-closed; async sempre `TenantContext.runWithTenant`; `knowledgeBases` vazio = zero retrieval; `kb_ids` server-side do vínculo; lookups `findByIdAndTenantId`; 404 uniforme.
- **Upload (R11-R13)**: `spring.servlet.multipart.max-file-size=10MB/max-request-size=12MB` (dev+prod); Tika com limite de texto + timeout + allowlist MIME magic-bytes; storage nome UUID fora do webroot; nunca `originalFilename` em path.
- **Embeddings (R14)**: credencial do tenant (mesmo `CredentialResolver`); sem env global compartilhada.
- **Resposta (R15-R16, R18)**: citações estruturadas do retrieval (`doc_id/chunk_id/score`); `rag_used=false` → citações vazias; contexto delimitado + instrução anti-seguir-instruções (prompt injection); logs só com IDs.
- **Throttling (R17)**: ingestão por tenant (padrão `ValidateRateLimiter`).

## 5. Modelo de Dados

```mermaid
erDiagram
    TENANT ||--o{ KNOWLEDGE_BASE : "possui"
    KNOWLEDGE_BASE ||--o{ KB_DOCUMENT : "contém"
    KNOWLEDGE_BASE ||--o{ AGENT_KB : "vincula"
    AGENT ||--o{ AGENT_KB : "usa"
    KNOWLEDGE_BASE {
        uuid id PK
        uuid tenant_id FK
        varchar name
        varchar slug
        varchar embedding_model
        smallint dimension
        varchar status
    }
    KB_DOCUMENT {
        uuid id PK
        uuid kb_id FK_CASCADE
        varchar source_type
        varchar source_uri
        varchar sha256_UNIQUE
        varchar status
        text error_NULL
    }
    KB_EMB_1536 {
        uuid embedding_id PK
        vector_1536 embedding
        text text
        jsonb metadata
        uuid kb_id
        uuid tenant_id
    }
```

- `knowledge_base`: `UNIQUE(tenant_id, slug)`; `status: ACTIVE|ARCHIVED`.
- `kb_document`: `status PENDING|INDEXING|READY|FAILED`; `sha256` p/ dedup; `error` sem stacktrace.
- `agent_knowledge_base(agent_id, kb_id, tenant_id)` — max 5 KBs/agente (validado no vínculo).
- Migração `Agent.knowledgeBases TEXT[]`: backfill — strings viram KBs placeholder por nome (`ON CONFLICT DO NOTHING`) + linhas na junção; leitura legada preservada 1 release.

## 6. Contratos de API

- `POST /v1/knowledge-bases {name, description?, embeddingModel?}` → 201 `{id,status}`.
- `GET /v1/knowledge-bases` (paginado, por tenant) → 200.
- `POST /v1/knowledge-bases/{id}/documents:upload` (multipart, PDF/TXT/MD) → 202 `{docId,status:PENDING}`; >10MB → 413; tipo fora → 400.
- `POST /v1/knowledge-bases/{id}/documents:url {url}` → 202 (SSRF guard; URL inválida/interna → 400/422 uniforme).
- `GET /v1/knowledge-bases/{id}/documents` → 200 (status por doc).
- `POST /v1/knowledge-bases/{id}/reindex` → 202 (idempotente: `removeAll(kb_id)` + reingestão).
- `DELETE /v1/knowledge-bases/{id}` → 204; com vínculo ativo → 409 `KB_IN_USE` + lista agentes; `?force=true` desvincula + cascade.
- `POST /v1/knowledge-bases/{id}/search {query, topK?}` → 200 `{chunks:[{text, score, docId, docName}], topK}` (playground + mesma função do chat).
- `PATCH /v1/agents/{id} {knowledgeBases:[kbId…max5]}` (campo existente; KB inexistente/outro tenant → 404).
- Chat inalterado no contrato; `ChatResponse` += `rag_used:bool` + `citations:[{docId,docName,chunkId,score}]`.

## 7. Pipeline de Ingestão (async)

`@Async` + `ThreadPoolTaskExecutor` (core 2/max 4/queue 50) + `TenantContext.runWithTenant(tenantId)` explícito **obrigatório**. Passos: Tika parse (limite texto/timeout) → `recursive(512 tokens, overlap 50, estimador OpenAI)` → prepend título → `EmbeddingStoreIngestor` (model da KB) → pgvector (`COMBINED_JSONB`, metadata `{tenant_id,kb_id,doc_id,chunk_index}`) → status READY/FAILED + `tokenUsage` logado. Reindex/delete: `removeAll(filter kb_id)` antes (idempotência/cascade físico).

## 8. Retrieval no Chat (manual)

Em `ChatServiceImpl` (pós-history, pré-`createClient`): se `agent.knowledgeBases` vazio → pula (zero retrieval). Senão: embed pergunta (model da(s) KB(s) — MVP: exige mesma `dimension` nas KBs vinculadas, 400 se mistas) → `search(maxResults 5, minScore 0.7, filter tenant+kb_ids server-side)` com timeout 2s (fallback: segue sem contexto) → `SystemMessage("Contexto relevante:\n...")` + `rag_used/citations`. Sem chunk ≥ threshold → responde sem contexto, `rag_used:false`, citações vazias.

## 9. Frontend (vinculante ux-designer — detalhe `.spec/frontend/knowledge-base/UX-SPEC.md`)

`/knowledge` (cards + busca + skeleton/empty + Tag PENDING/INDEXING/READY/FAILED), `/knowledge/new|/:id/edit` (tabs Documentos|Agentes|Testar), drag-drop FileUpload + URL + lista com reindex, playground (Textarea + Slider topK + chunk cards com score), picker no editor (max 5 + contador), item de menu `Bases de Conhecimento` (`pi pi-book`, índice 3), breadcrumb PT-BR, aria, dark mode.

## 10. Requisitos Funcionais (CA1-CA6 do PM — BDD na SPEC)

CA1 PDF→resposta citada (`rag_used:true`); CA2 URL citada como fonte; CA3 KB desvinculada não contamina (`rag_used:false`); CA4 PDF corrompido → FAILED sem 500; CA5 delete vinculado → 409 + lista / `?force=true` → 204; CA6 cross-tenant → 404. RN-KB1–RN-KB7 (ownership, papéis, limites 10/50/10MB, ciclo de vida, N:N max 5, delete com force, RAG só com fonte) — SSoT futura em `SYSTEM_SPECIFICATION.md` (seção Knowledge).

## 11. Security Assessment — Fase 1

STRIDE/auditoria completos no relatório do `security-engineer` (18 requisitos R1-R18 acima, §4). **Verdict: CONDICIONAL** — 10 🔴 vinculantes + 8 🟡 (todos endereçados como tasks na Fase 2). Achados pré-existentes (JWT HS256, permissões hardcoded) fora de escopo, registrados como contexto.

## 11.5. Padrões de implementação (regras canônicas)
- Sem Lombok; MapStruct (`@Mapper(componentModel="spring")`) em mappers domínio/DTO; repositórios `JpaRepository<Entity,UUID>`; `AbstractEntity` (tenant filter) nas 4 tabelas; `@HasPermission` + `@Valid` em todo endpoint; `when_*_then_*`; `*IT.java` só em `backend/integration-test/` com `DatabaseCleaner`.

## 12. Estratégia de Testes

- **Unitários**: splitter/ingest mockado, resolver precedência, `ValidateRateLimiter`-like, validators (prefixo/URL/SSRF guard), converter? (sem segredo novo).
- **Integração** (`backend/integration-test/`, PG16+pgvector via Testcontainers): CRUD + `search` com KB seedada; **IDOR cross-tenant** (retrieval + CRUD); SSRF (loopback/redirect/metadata); V11/V12 + migração `TEXT[]`; upload 413/400.
- **Frontend**: 2 views + service/types + picker + menu + breadcrumb; playground mockado.
- **Cobertura**: 80% service / 90% domain (BE), 70% (FE).

## 13. Migração / Rollback

V11 core + V12 embeddings (tabelas por dimensão iniciais: `_1536`, `_768`); backfill `TEXT[]`; rollback = DROP tabelas knowledge + código anterior (dados vetoriais descartáveis; docs re-uploadáveis).

## 14. Delta Specs

### Delta: `docs/product/SYSTEM_SPECIFICATION.md`
### ADDED
> **Seção alvo**: nova seção `Knowledge / RAG`
```
RN-KB1–RN-KB7, limites MVP, ciclo de vida, playground, citações, permissões KNOWLEDGE_* — conforme SPEC knowledge-base.
```

### Delta: `docs/adr/`
### ADDED
```
ADR-010-KB-RAG-pgvector-manual.md (criar nesta Fase 1)
```

### Delta: `AGENTS.md`
### MODIFIED (se aplicável)
> **Seção alvo**: lista de módulos backend
```
Incluir módulo knowledge/ (api/impl/dbupdate/web) quando criado.
```

### Resumo do Impacto

| Documento | ADDED | MODIFIED | REMOVED |
|---|---|---|---|
| `docs/product/SYSTEM_SPECIFICATION.md` | 1 (seção Knowledge) | 0 | 0 |
| `docs/adr/` | 1 (ADR-010) | 0 | 0 |
| `AGENTS.md` | 0 | 0-1 | 0 |
