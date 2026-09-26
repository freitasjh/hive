# Hive - System Specification & Project Rules

Este documento estabelece as diretrizes fundamentais, arquitetura e padrões técnicos do projeto Hive. O Hive é uma plataforma avançada para gerenciamento e execução de Assistentes e Agentes de IA, permitindo orquestração complexa e integração com múltiplos modelos de linguagem.

## 🏗 1. Arquitetura do Sistema
O Hive é estruturado como um **Modular Monolith** fundamentado em **Bounded Contexts (DDD)**.

- **Módulos Principais**:
    - `identity`: Gestão de usuários e permissões.
    - `tenant`: Isolamento de dados e faturamento por cliente.
    - `agent`: Core do sistema; cadastro de agentes, assistentes e prompts.
    - `security`: Proteção de acessos e auditoria.
    - `commons`: Utilitários e ferramentas transversais.
- **Isolamento de Dados**: Cada módulo gerencia seu ciclo de vida de banco de dados via migrações Flyway independentes.

## 🛠 2. Stack Tecnológica (Explícita)

### Backend
- **Linguagem**: **Java 25**
- **Framework**: **Spring Boot 4.0.3** (framework único de IA: **LangChain4j** — Spring AI removido, ver ADR-009).
- **IA Core**: **LangChain4j** para comunicação com LLMs.
- **Orquestração de IA**: **LangGraph4j** para fluxos cíclicos e complexos de agentes.
- **Migrations**: **Flyway 10.10.0**.
- **Database**: **PostgreSQL** com uso nativo de **UUID v7** para chaves primárias.

### Frontend
- **Framework**: **Vue 3** (Composition API com `<script setup lang="ts">`).
- **Linguagem**: **TypeScript**.
- **UI Library**: **PrimeVue** (Design inspirado em Gemini/ChatGPT).
- **State Management**: **Pinia**.

## ⚖️ 3. Regras de Governança (GEMINI.md)
- **Mandato de Delegação Absoluta**: O Orquestrador planeja; especialistas (`@backend-specialist`, `@frontend-specialist`) codificam.
- **Code Review**: Obrigatório para todas as tarefas (veredito `APPROVED`).
- **Definição de Pronto (DoD)**: Código implementado, testes unitários/integração aprovados, documentação atualizada e aceite do usuário.

## 🧠 4. Domínio: Inteligência Artificial e Agentes
O Hive permite a criação de ecossistemas de agentes especializados.

- **Agentes**: Entidades com personas definidas e instruções específicas.
- **Prompt Templates**: Versionamento e gestão de prompts para garantir consistência.
- **Assistentes**: Interfaces de execução que utilizam agentes cadastrados para interações web.
- **Orquestrador**: Uso de **LangGraph4j** para definir lógica de decisão e fluxos entre múltiplos agentes.
- **API Pública**: Preparação para consumo futuro de agentes via chamadas externas.

## 🏦 5. Regras Financeiras e Auditabilidade
Mesmo focado em IA, o sistema mantém rigor financeiro (especialmente no módulo `tenant`).

- **Precisão Monetária**: Uso obrigatório de **`BigDecimal`**.
- **Padrão Ledger**: Saldos e consumos de tokens devem ser registrados de forma imutável para auditoria.
- **Multi-tenancy**: Isolamento absoluto de dados por `tenant_id` em todas as operações.

## 💻 6. Padrões de Código
- **Backend (Java 25)**: Sem Lombok, sem `var`, uso de **Records** para DTOs e mensagens. Exceções via **RFC 7807**.
- **Frontend (Vue 3)**: Uso de **Composition API** e PrimeVue. Proibido o uso de `any` em TypeScript.

## 🧪 7. Estratégia de Testes
- **Unitários**: Obrigatórios para lógica de domínio e conversores.
- **Integração**: Obrigatórios para endpoints e serviços de IA, utilizando **Testcontainers** para instanciar PostgreSQL.
- **Qualidade**: `mvn clean test` e `mvn integration-test -Pintegration` devem passar integralmente.

## ⌨️ Comandos Úteis
```bash
# Backend
sdk use java 25.0.2-oracle # seta a versão correta do java para o projeto
mvn spring-boot:run              # Iniciar aplicação
mvn clean test                   # Testes unitários
mvn integration-test -Pintegration # Testes de integração

# Frontend
npm run dev                      # Servidor de desenvolvimento
npm run test                     # Executar testes unitários
```

## 4.1 Chat Persistente Multi-turn (ADR 007 — 2026-08-26)

- **Use case Chat**: `POST /v1/chat` com `sessionId` opcional (ausente→cria session title 50 chars, presente→append + valida anti-IDOR 404), multi-turn com history window 20 ASC, TX split (TX1 userMsg / LLM fora TX / TX2 assistant+touch atômico).
- **Session CRUD**: `POST /v1/chat/sessions 201`, `GET /v1/chat/sessions Page updateAt DESC 200`, `GET /v1/chat/sessions/{id} 200/404`, `GET /v1/chat/sessions/{id}/messages Page createAt ASC 200/404`, `DELETE 204 cascade ON DELETE`, `PATCH rename 200`, `GET /v1/agents Page 200`. Todos `@HasPermission` + `@Valid` + tenant filter + Pageable (max 50/100 →400).
- **Persistência**: `chat_session(user_id, agent_id NOT NULL FK agent ON DELETE CASCADE, tenant_id NOT NULL, title 50, summary TEXT, createAt, updateAt)` 1--* `chat_message(session_id FK CASCADE, tenant_id NOT NULL, role USER|ASSISTANT, content TEXT, createAt)` + V5 índices.
- **Regras**: title truncate 50 sem "...", agentId imutável por sessão, anti-IDOR 404 (não 403), hard delete cascade, window 20, RFC7807 400, ProviderException 502 preserva userMsg.

## 4.2 Administração de Prompts e Agentes + Agente Padrão (ADR 008 — 2026-08-30)

- **Prompt Admin**: `POST /v1/prompt-templates` `GET /prompt-templates?page&q&status` `GET /{id}` `PUT /{id}` `DELETE /{id}` com validação `{{var}}` regex, code imutável, version++, params autoritativo, tags/description, inferenceParams (temperature/topP/maxTokens), playground extrai vars e preview mock local.
- **Agent Admin**: `POST /v1/agents` `GET /v1/agents?page&q` `GET /{id}` `PUT /{id}` `DELETE /{id}` `POST /{id}/default` `GET /default` com `is_default` exclusividade 1/tenant via partial unique + split 2 UPDATEs atomico, avatar URL `^https?://.*`, systemPrompt vínculo Prompt, tools/KBs string[] placeholder, herança params sobrescreve, badge Padrão ⭐, sole 409, inactive 422.
- **Fallback**: `POST /v1/chat agentId=null` → `DefaultAgentResolver.requireDefault(tenantId)` usa padrão, 422 `DEFAULT_AGENT_NOT_FOUND` se ausente (command), `GET /default` 404 query.
- **Migrations**: V6 is_default partial unique, V7 prompt TEXT/tags, V8 FK RESTRICT, idempotente.
- **RN-AG1 (delete em cascata — destrutivo)**: `DELETE /v1/agents/{id}` remove **permanentemente** o agente **e todas as suas `chat_session`, as `chat_message` delas e os vínculos `agent_knowledge_base`**. Não há lixeira, soft delete nem recuperação. Motivo (2026-09-26, P0): `chat_session.agent_id` era `NO ACTION` e um único histórico de chat travava a exclusão com 409 — nenhum agente do tenant era excluível; decisão do usuário A1 = `ON DELETE CASCADE`. Mitigação: o diálogo de confirmação da tela de agentes declara a remoção das conversas/mensagens/vínculos (`role=alert`) e o botão diz "Excluir definitivamente". Anti-IDOR: agente de outro tenant → 404 e nada é cascateado. Error de integridade remanescente (ex.: template em uso, FK RESTRICT V8) → 409 com mensagem pt-BR acionável e **sem** nome de tabela/coluna/constraint no corpo (OWASP A05; o detalhe do driver fica só no log).
- **Migrations do cascade (V14)**: `agent/V14__agent_delete_cascade.sql` → `chat_session.agent_id ON DELETE CASCADE` (tabela dona: módulo `agent`). `knowledge/V14__agent_kb_agent_fk_cascade.sql` → `agent_knowledge_base.agent_id ON DELETE CASCADE` + purga de órfãos (a junção **não** tinha FK para `agent`; sem ela o cascade trocava o 409 por inconsistência silenciosa). A FK da junção vive no módulo **`knowledge`** porque a tabela é dele (V11 a criou, `AgentKbEntity` a mapeia, `KbAgentLinkPort` é a fronteira agent→knowledge); a dependência cruzada knowledge→agent já existia (V13 lê `FROM agent`) e a ordem de migração passou a ser explícita em `KnowledgeDbUpdateConfig` (`@DependsOn("agentFlyway")`).

## 4.3 Vendor real de LLM — provider = vendor (ADR-009 — 2026-09-25, Aceito; GO/Zen/custom: ADR-011 — 2026-09-25, Aceito)

- **Framework único**: LangChain4j. Módulo legado `provider/spring-ai` removido por completo (poms, factory, frontend). Sem dual-framework.
- **Enum `AiProvider`**: `OPENAI | GEMINI | ANTHROPIC | OLLAMA | OPENCODE_GO | OPENCODE_ZEN | OPENAI_COMPATIBLE` (Grok/Mistral nativos = fase 2 via `OPENAI_COMPATIBLE` + baseUrl). `VENDOR_ORDER` FE espelha os 7 itens; `OPENAI_COMPATIBLE` rotulado "Custom (endpoint próprio)", `hiddenFromCreate=false`.
- **OpenCode GO/Zen**: baseUrls fixas server-side (`https://opencode.ai/zen/go/v1`, `https://opencode.ai/zen/v1` — nunca do tenant; o `/v1` é obrigatório porque o LangChain4j concatena apenas `/chat/completions` ao baseUrl); defaults GO `mimo-v2.5`, Zen `claude-sonnet-4-5`; sem validação de prefixo de chave; envs `OPENCODE_GO_API_KEY`/`OPENCODE_ZEN_API_KEY` (credencial do tenant tem precedência; env só single-tenant).
- **Discovery**: `GET /v1/providers/{go,zen}/models` (`@HasPermission AGENT_MANAGE` + rate-limit) → `200 {models, filteredOut, cachedAt}`; client pinado (sem redirect, TLS com `endpointIdentificationAlgorithm=HTTPS` no handshake — nunca `HttpsURLConnection.getDefaultHostnameVerifier()`, que é stub always-false no JDK; timeout 5s, 512KB, schema `[A-Za-z0-9._:-]{1,128}`, cap ~300, denylist responses/messages); cache 10min + refresh manual; evict em `MODEL_UNAVAILABLE`; falha remota → 422 `DISCOVERY_FAILED`; nada persiste. FE: `useRemoteModels` (loading/empty/error/stale 5min + refresh), badge "fora do catálogo" não-bloqueante.
- **Custom**: `OPENAI_COMPATIBLE` first-class (baseUrl editável + chave); guard estendido (CGNAT 100.64/10 bloqueado, https obrigatório p/ não-loopback, pin de IP no call do chat; loopback permitido).
- **Headers Go**: `x-opencode-session` + `User-Agent: hive/1.0` definidos server-side pela factory via `customHeaders` (sem input path do cliente), só GO/Zen; o session é o UUID da `chat_session` (estável no multi-turn), nunca logado.
- **Chat/custo**: modelo removido → 422 `MODEL_UNAVAILABLE` ("Modelo {m} indisponível no provedor {p}. Escolha outro modelo ou atualize a lista."), sem fallback, userMsg preservada; 404 só é lido como "modelo removido" quando o corpo do vendor é JSON citando o modelo — 404 de rota (HTML) vira `ProviderException` 500, que é o diagnóstico correto; rate limit de chat por tenant + log de uso (tokens) desde o dia 1; `key_version++` na rotação. Teto mensal + alertas = fase 2.
- **LGPD**: banner `Message info` no editor (GO/ZEN) + dialog de credencial (2 superfícies, conforme UX-SPEC 6; a 3ª — nota em KB — foi cortada do escopo): "🔒 Privacidade — Seus prompts e dados de conversa trafegam pela API da OpenCode. Consulte a política de privacidade: https://opencode.ai/privacy".

- **Framework único**: LangChain4j. Módulo legado `provider/spring-ai` removido por completo (poms, factory, frontend). Sem dual-framework.
- **`model: String` catalogado**: validado por catálogo por vendor no backend (BE é autoridade; FE só espelha para UX); fora do catálogo → 400 `MODEL_NOT_SUPPORTED_FOR_PROVIDER` (`OPENAI_COMPATIBLE` aceita string livre, valida só conectividade).
- **Credenciais por tenant**: tabela `provider_credential(tenant_id, provider, ciphertext, key_version, base_url?)`, `UNIQUE(tenant_id, provider)` + `FK tenant ON DELETE CASCADE`; AES-256-GCM via `AttributeConverter` (IV/nonce random por linha, chave mestra em env `PROVIDER_MASTER_KEY`, nunca no repo).
- **Mascaramento**: `GET /v1/provider-credentials` retorna só `{provider, configured, last4, updatedAt}` — nunca ciphertext; `PUT /v1/provider-credentials/{provider}` valida formato por prefixo (`sk-` OPENAI, `AIza` GEMINI, `sk-ant-` ANTHROPIC) → 400 se inválido; `DELETE` → 204. Tudo sob `@HasPermission("AGENT_MANAGE")` + filtro de tenant (anti-IDOR).
- **Validate**: `POST /v1/agents/validate {provider, model, baseUrl?}` → `200 {ok, provider, model, latencyMs, reason?}`; usa a chave do tenant (sem `apiKey` no body); nunca persiste; falha do vendor → `ok:false` (sem repassar status do vendor); timeout 5s; throttling por tenant.
- **Resolução de chave (chat)**: credential do tenant → fallback env → `422 CONFIG_MISSING` (Ollama: sem chave). Chave ausente nunca vira 500.
- **Migrações**: V9 cria `provider_credential` + V10 migra dados legados (`UPDATE` por `llm_model`, idempotente; `OPENCODE→OPENAI_COMPATIBLE` + baseUrl gateway); colunas `provider_type`/`llm_model` reaproveitadas (zero DDL estrutural em `agent`); `temperature/topP/maxTokens/tools/KB/isDefault` preservados.
- **Blast radius**: breaking change — release único BE+FE; chave é global por tenant (rotação central; override por agente = evolução futura).
- **OUT de escopo**: cotas/limites de uso por tenant (P2 roadmap); vendors nativos além do MVP (fase 2 via compatible).

## 4.4 Base de Conhecimento / RAG — pgvector, módulo `knowledge/` (ADR-010 — 2026-09-25, Aceito)

- **Bounded context**: módulo `knowledge/` (`api/impl/dbupdate/web/internal/v1`); KBs, documentos, chunks/embeddings, vínculo agente↔KB, playground. `agent/` consome via `knowledge/api` (retrieval no chat).
- **RN-KB1 (ownership)**: toda KB e documento pertencem a um tenant (`tenant_id NOT NULL`); cross-tenant → 404 uniforme (anti-IDOR).
- **RN-KB2 (papéis)**: `KNOWLEDGE_MANAGE` (CRUD, upload, URL, reindex, vínculo) e `KNOWLEDGE_READ` (leitura + playground); chat com RAG exige vínculo do agente (só `KNOWLEDGE_READ` não autoriza retrieval arbitrário).
- **RN-KB3 (limites MVP)**: arquivo máx. 10MB (request 12MB; >10MB → 413); tipos PDF/TXT/MD com magic-bytes (fora → 400); URL máx. 2MB via stream; PDF escaneado (imagem) não suportado; chunking recursive 512 tokens/overlap 50.
- **RN-KB4 (ciclo de vida)**: documento `PENDING → INDEXING → READY | FAILED` (erro sem stacktrace); reindex idempotente (`removeAll(kb_id)` + reingestão); URL é snapshot único (sem recrawl).
- **RN-KB5 (N:N max 5)**: junção `agent_knowledge_base`; máx. 5 KBs/agente (6ª → 400); KBs vinculadas exigem mesma dimensão (mix 1536+768 → 400 com mensagem PT-BR no picker).
- **RN-KB6 (delete)**: delete com vínculo ativo → 409 `KB_IN_USE` + lista de agentes; `?force=true` desvincula + cascade (204).
- **RN-KB7 (RAG só com fonte)**: retrieval `top 5, minScore 0.7, timeout 2s` (fallback: segue sem contexto); sem chunk ≥ threshold → `rag_used:false`, citações vazias; resposta com `rag_used + citations[{docId,docName,chunkId,score}]`; contexto delimitado anti-prompt-injection.
- **Contratos**: `POST /v1/knowledge-bases 201` · `GET /v1/knowledge-bases` (paginado) · `POST /{id}/documents:upload 202` (multipart) · `POST /{id}/documents:url 202` (SSRF guard) · `GET /{id}/documents` · `POST /{id}/reindex 202` · `DELETE /{id} 204/409` · `POST /{id}/search {query,topK} 200` (playground, mesma função do chat) · `PATCH /v1/agents/{id} {knowledgeBaseIds}` (vínculo) · `GET /v1/agents/{agentId}/knowledge-bases`.
- **Playground (FE)**: aba Testar na edição da base — Textarea + Slider topK 1–10 (default 5) + chunk cards com score badge (≥0.90 alta, ≥0.70 média) e fonte arquivo; picker de KBs no editor de agente (max 5 + contador, mix de dimensões bloqueado).
- **Segurança**: SSRF guard dedicado (loopback/CGNAT bloqueados, `followRedirects NEVER` + revalidação ≤3 hops, anti-rebinding, portas 80/443, timeouts 5s/10s); `WHERE tenant_id` explícito em toda query vetorial nativa; async sempre com `TenantContext.runWithTenant`; ingestão com rate limit por tenant; embeddings com credencial do tenant (sem env global).
- **Persistência**: `knowledge_base(UNIQUE tenant,slug; status ACTIVE|ARCHIVED)` + `kb_document(status, sha256 dedup, error)` + `agent_knowledge_base` (V11) + `kb_embeddings_{1536,768}` JSONB + índices vetoriais (V12); backfill `Agent.knowledgeBases TEXT[]` → KBs placeholder (V13); `Flyway` history própria `flyway_schema_history_knowledge`.
