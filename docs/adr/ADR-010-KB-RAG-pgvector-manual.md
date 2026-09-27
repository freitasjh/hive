# ADR-010 — Base de Conhecimento: RAG manual com pgvector (módulo `knowledge/`)

- **Status:** Aceito (Fase 2 SDD — `knowledge-base`, Batch 2/2 implementado)

> ### ⚠️ Supersedido em 2026-09-27 — decisão `scope-assistant-sem-rag`
>
> Este ADR **não foi reescrito**: ele é registro do raciocínio da época, e reescrever seria apagar a
> lição. A decisão está em **[ADR-013 — Assistant, sem base de conhecimento e sem agente](ADR-013-Assistant-Sem-Rag-Sem-Agente.md)**.
> O Brain (`arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`) é registro secundário e
> complementar, não fonte primária.
>
> O produto virou um **Assistant**: chat simples, sem agente, sem tools, sem base de conhecimento.
> O módulo `knowledge/` inteiro, o pipeline de RAG e os endpoints de vínculo agente↔KB foram **deletados**.
Nenhuma migration de drop: as tabelas continuam no banco, órfãs. As permissões `KNOWLEDGE_*` também
sairam — nenhuma era verificada por endpoint desde a remoção do único controller que as usava, e
concedê-las colocava no token duas permissões que não destravavam nada.

- **Data:** 2026-09-25
- **Contexto:** Hive AI (Java 21 + Spring Boot 4.1.0 + LangChain4j 1.19.0 + pgvector/pgvector:pg16)
- **Decisores:** usuário (módulo separado) + architect + product-manager + ux-designer + security-engineer

## Contexto

Agentes sem RAG não respondem sobre documentos do tenant. `Agent.knowledgeBases TEXT[]` é placeholder sem entidade.

## Decisão

1. Módulo DDD `knowledge/` separado (api/impl/dbupdate/web), Flyway history própria.
2. Retrieval **manual** (`retriever.retrieve` + SystemMessage) — AiServices exigiria reescrever sessões/TX.
3. **1 tabela de embeddings por dimensão** (`kb_embeddings_{dim}`); router pela `kb.embedding_model/dimension`; default OpenAI 1536 + Ollama 768.
4. **Flyway próprio**, `PgVectorEmbeddingStore.createTable=false`, metadata `COMBINED_JSONB`.
5. Async MVP `@Async` + `ThreadPoolTaskExecutor` com `TenantContext.runWithTenant` explícito (ScopedValue não propaga).
6. Chunking `recursive(512, 50)` + título prepended (tunable via playground).
7. Credencial de embedding = mesma do tenant (sem env global); junção N:N `agent_knowledge_base` (max 5); playground `POST .../search`.
8. Vetorial-only no MVP (HYBRID reservado); RabbitMQ/OCR/recrawl/versionamento fase 2.

## Alternativas Consideradas

| Alternativa | Motivo da rejeição |
|---|---|
| Easy RAG | Qualidade PoC, sem controle de tenant/chunking |
| AiServices + contentRetriever | Reescreveria TX/sessões/memória custom do chat |
| `createTable=true` | DDL fora do Flyway quebra versionamento/review |
| ONNX local default | Qualidade PT-BR fraca; custo OpenAI é baixo |
| Dentro de `agent/` | Usuário decidiu módulo separado (escala p/ outros BCs) |
| RabbitMQ já no MVP | Complexidade sem necessidade (fila durável só se volume exigir) |

## Consequências

- **Positivas:** RAG tenant-isolado, auditável, testável; migração `TEXT[]` preserva dados; playground valida qualidade.
- **Negativas:** 18 requisitos de segurança vinculantes (SSRF/Tika/tenant); V11/V12 + migração; `@Async` exige disciplina de contexto.
- **Segurança:** auditoria pré-SPEC com 10 🔴 vinculantes (todos como tasks Fase 2).

## Conformidade

- [ ] SPEC `.spec/backend/knowledge-base/SPEC.md`
- [ ] Flyway V11/V12 + extension vector
- [ ] ITs IDOR + SSRF obrigatórios (gate de qualidade)
