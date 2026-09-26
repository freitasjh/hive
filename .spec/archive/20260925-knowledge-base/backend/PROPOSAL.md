# Change Proposal: Base de Conhecimento / RAG (pgvector)

> **ID**: `knowledge-base`
> **Status**: `Under Review`
> **Author**: orchestrator (Fase 1 SDD)
> **Date**: 2026-09-25
> **Source**: triagem Fase 0 (architect + product-manager) + estudo Brain `estudos/global/langchain4j/rag-pgvector-completo`

---

## 1. Problem Statement

- **Contexto**: agentes só conhecem `systemPrompt`; não respondem sobre documentos do tenant. `Agent.knowledgeBases: TEXT[]` é placeholder sem entidade/FK/validação, nunca lido no chat.
- **Impacto atual**: sem RAG, agente não serve manuais/políticas/docs; sem ingestão, sem vínculo real, sem citação de fonte.
- **Evidência**: `Agent.java:26` + `V7 knowledge_bases TEXT[]`; ROADMAP Phase 2 prevê módulo `knowledge` (XL) sem regras; docs LangChain4j RAG + pgvector validados no estudo.

## 2. Proposed Solution

**Resumo**: módulo DDD `knowledge/` (decisão usuário): CRUD de KBs + documentos (upload Tika + URL) + ingestão async (chunking + embeddings) em pgvector com filtro tenant + retrieval manual no `ChatServiceImpl` (SystemMessage + `rag_used`/citações) + vínculo N:N agente↔KB + playground de teste.

**Abordagem**:
- Flyway próprio (`flyway_schema_history_knowledge`): `knowledge_base`, `kb_document`, `kb_embeddings_{dim}` (1 tabela/dimensão), junção `agent_knowledge_base`; `createTable=false` no store.
- Retrieval manual (blast radius mínimo; AiServices exigiria reescrever sessões); `PgVectorEmbeddingStore` via `datasourceBuilder` (Hikari); metadata JSONB `{tenant_id,kb_id,doc_id,chunk_index}`.
- Chunking `recursive(512, 50)` + título prepended; embedding default OpenAI `text-embedding-3-small` (1536) + Ollama `nomic-embed-text` (768); credencial de embedding = mesma do tenant (sem env global compartilhada).
- Async MVP: `@Async` + `ThreadPoolTaskExecutor` com `TenantContext.runWithTenant` explícito (ScopedValue não propaga); RabbitMQ fase 2.
- Alternativas descartadas: Easy RAG (qualidade PoC), AiServices (reescrita chat), `createTable=true` (quebra versionamento), ONNX local default (PT-BR fraco), híbrida no MVP (OUT — PgVector HYBRID reservado fase 2).

## 3. Scope

### In Scope
- [ ] Módulo `knowledge/` (api/impl/dbupdate/web) + `CREATE EXTENSION vector`
- [ ] CRUD KB + documentos (upload PDF/TXT/MD 10MB + URL com SSRF guard dedicado) + status PENDING/INDEXING/READY/FAILED + reindex idempotente
- [ ] Pipeline async (Tika→recursive(512,50)→embed→pgvector, `sha256` dedup, `IngestionResult.tokenUsage` logado)
- [ ] Retrieval no chat (`maxResults 5`, `minScore 0.7`, timeout 2s + fallback, `rag_used` + citações) + junção N:N (max 5 KBs/agente) + migração `TEXT[]`
- [ ] Playground `POST /v1/knowledge-bases/{id}/search` + telas (`KnowledgeBaseList/Detail`, picker no editor, item de menu)
- [ ] Permissões `KNOWLEDGE_MANAGE`/`KNOWLEDGE_READ` + ITs (IDOR cross-tenant, SSRF, migração) + SPEC seção Knowledge no produto + ADR

### Out of Scope
- [ ] Recrawl automático, OCR, versionamento de doc, busca híbrida, permissões por documento, cotas/billing, multi-chave, Grok/Mistral nativos (fase 2)

## 4. Architecture Impact

| Aspecto | Impacto | ADR Necessário? |
|---------|---------|-----------------|
| Backend (novo `knowledge/*`, `agent` chat) | Novo módulo + retrieval no `ChatServiceImpl` + junção | Sim |
| Frontend (2 views, service, types, menu, picker) | Telas KB + playground + picker | Não (UX-SPEC) |
| Banco | 4 tabelas + extension vector + V11/V12 | Sim |
| Multi-tenancy | `tenant_id NOT NULL` + filtro em toda query vetorial + IT IDOR | Não (padrão) |
| Segurança / RBAC | 2 permissões novas + SSRF guard + Tika guards + AES? não (sem segredo novo) | Sim (security-review) |
| Eventos | Nenhum (async local; RabbitMQ fase 2) | Não |

## 5. Delta Preview

| Documento Alvo | Tipo de Mudança | Seção Afetada |
|----------------|-----------------|---------------|
| `docs/product/SYSTEM_SPECIFICATION.md` | ADDED | Seção Knowledge/RAG |
| `docs/adr/` | ADDED | `ADR-010-KB-RAG-pgvector-manual.md` |
| `AGENTS.md` | MODIFIED | Seção módulos (se listar módulos) |

## 6. Risk Assessment

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Cross-tenant leak (async sem tenant / nativa sem filtro) | Média | Alto | `runWithTenant` + `WHERE tenant_id` explícito + IT IDOR (R5-R8) |
| SSRF via URL arbitrária | Média | Alto | Guard dedicado + redirect NEVER + pin IP + IT SSRF (R1-R4) |
| Custo embeddings sem teto | Média | Médio | Limites RN-KB3 + dedup sha256 + log tokens (billing fase 2) |
| Tika bombs / polyglot | Baixa | Alto | 10MB + magic-bytes + limite texto + timeout (R11-R13) |
| Migração `TEXT[]` ambígua | Média | Médio | Backfill por nome + leitura legada 1 release (RN PM) |

## 7. Success Criteria

- [ ] Upload PDF → pergunta respondida com citação (`rag_used:true`, fonte clicável)
- [ ] URL de docs indexada e citada como fonte
- [ ] KB desvinculada não contamina resposta (`rag_used:false`)
- [ ] Cross-tenant: 404 sem vazamento (IT)
- [ ] `mvn clean install` + `mvn verify -pl integration-test` + `npm run test` + `npm run build` verdes
- [ ] Harness: cadastro KB → upload → pergunta → resposta citada < 5min

## 8. Completion Metadata

- **Archived on**: — · **Duration**: — · **Archive path**: — · **Files changed**: —
