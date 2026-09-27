# Hive - System Specification & Project Rules

Este documento estabelece as diretrizes fundamentais, arquitetura e padrões técnicos do projeto Hive.

> **Decisão de produto (2026-09-27): o Hive é um ASSISTANT, não uma plataforma de agentes.** Chat
> simples, sem agente, sem tools, sem base de conhecimento, não-agentico. O prompt é escolhido **por
> conversa**; o provedor e o modelo são um **default do tenant**. Decisão registrada em
> `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`. As seções marcadas como
> **registro do que foi removido** descrevem o que existiu, não o que existe — e não devem ser lidas
> como especificação vigente.

## 🏗 1. Arquitetura do Sistema
O Hive é estruturado como um **Modular Monolith** fundamentado em **Bounded Contexts (DDD)**.

- **Módulos Principais**:
    - `identity`: Gestão de usuários e permissões.
    - `tenant`: Isolamento de dados e faturamento por cliente.
    - `agent`: Core do sistema; chat multi-turn, sessões e prompts. O módulo **mantém o nome**
      histórico `agent` — renomeá-lo é batch próprio, com impacto em pacote de dependência, Flyway history
      e imports.
    - `security`: Proteção de acessos e auditoria.
    - `commons`: Utilitários e ferramentas transversais.
- **Isolamento de Dados**: Cada módulo gerencia seu ciclo de vida de banco de dados via migrações Flyway independentes.

## 🛠 2. Stack Tecnológica (Explícita)

### Backend
- **Linguagem**: **Java 25**
- **Framework**: **Spring Boot 4.0.3** (framework único de IA: **LangChain4j** — Spring AI removido, ver ADR-009).
- **IA Core**: **LangChain4j** para comunicação com LLMs.
- **Orquestração de IA**: **nenhuma**. Não há grafo, ciclo nem decisão de rota entre modelos — o
  produto é uma conversa, e um orquestrador aqui seria complexidade sem caso de uso.
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

## 🧠 4. Domínio: o Assistant
O produto é uma conversa. Não há entidade de agente, não há persona, não há tools, não há retrieval.

- **Aggregate root**: a própria `chat_session`. Ela é a conversa; o que a responde é parâmetro dela.
- **Prompt Templates**: Versionamento e gestão de prompts. Escolhidos **na criação da conversa** e
  imutáveis depois — a sessão guarda `prompt_template_id NOT NULL FK prompt_template ON DELETE RESTRICT`.
- **Provedor e modelo**: **default do tenant**, uma linha só, em `tenant_llm_config`. Sem fallback
  global e sem criação automática: sem essa linha o chat recusa com 422 dizendo o que configurar.
- **Credencial**: por tenant e por provedor, em `provider_credential` (módulo `agent`).
- **Limites de geração** (`temperature`/`topP`/`maxTokens`): moram no **prompt template**
  (`inference_params` JSONB), não no modelo e não na sessão.

> **Por que o agente existia e por que morreu.** O agente reunia prompt **e** modelo **e** limites num
> só registro, o que obrigava a conversa a escolher um objeto que mudava de significado conforme o
> tenant. Separando as duas responsabilidades — prompt por conversa, modelo por tenant — não sobra
> nada para a entidade fazer.

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
- **Session CRUD**: `POST /v1/chat/sessions 201`, `GET /v1/chat/sessions Page updateAt DESC 200`, `GET /v1/chat/sessions/{id} 200/404`, `GET /v1/chat/sessions/{id}/messages Page createAt ASC 200/404`, `DELETE 204 cascade ON DELETE`, `PATCH rename 200`. Todos `@HasPermission` + `@Valid` + tenant filter + Pageable (max 50/100 →400).
- **Persistência**: `chat_session(user_id, prompt_template_id NOT NULL FK prompt_template ON DELETE RESTRICT, tenant_id NOT NULL, title 50, summary TEXT, createAt, updateAt)` 1--* `chat_message(session_id FK CASCADE, tenant_id NOT NULL, role USER|ASSISTANT, content TEXT, createAt)` + V5 índices.
- **Regras**: title truncate 50 sem "...", `promptTemplateId` imutável por sessão (um id divergente no request é ignorado e logado, nunca aplicado), anti-IDOR 404 (não 403), hard delete cascade, window 20, RFC7807 400, ProviderException 502 preserva userMsg.
- **Ordem de resolução do turno** (2026-09-27): template pela sessão → provider/model por `tenant_llm_config.require` (422 `TENANT_LLM_CONFIG_NOT_FOUND` se ausente) → `VendorCatalog.validate` → credencial (422 `CONFIG_MISSING`) → TX1 → geração → TX2.

## 4.2 Prompts (o que era "Administração de Prompts e Agentes" — ADR 008, reduced 2026-09-27)

> **Registro do que foi removido nesta seção:** `AgentEntity`, `AgentService`, `AgentControllerV1`,
> `DefaultAgentService`, a tabela `agent` e as rotas `/v1/agents*` (exceto o probe, que virou
> `/v1/providers/validate`). O **default do tenant** passou a ser `tenant_llm_config`; o **prompt** é
> escolhido por conversa. Decisão em `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`.

- **Prompt Admin (inalterado)**: `POST /v1/prompt-templates` `GET /prompt-templates?page&q&status` `GET /{id}` `PUT /{id}` `DELETE /{id}` com validação `{{var}}` regex, code imutável, version++, params autoritativo, tags/description, inferenceParams (temperature/topP/maxTokens), playground extrai vars e preview mock local.
- **RN-PT1 (template em uso)**: `DELETE /v1/prompt-templates/{id}` com sessão apontando → 409
  `PROMPT_IN_USE`, mensagem pt-BR ("em uso por uma sessão de chat") e **sem** nome de
  tabela/coluna/constraint no corpo (OWASP A05; o detalhe do driver fica só no log). A checagem é
  na aplicação (`existsByPromptTemplateIdAndTenantId`); o `ON DELETE RESTRICT` é a rede de segurança.
- **RN-PT2 (o prompt é da conversa)**: a sessão guarda o template. Um `promptTemplateId` divergente
  enviado no chat é **ignorado**, não aplicado — uma conversa que já tem mensagens não pode ser
  re-direcionada por um cliente que ainda tem a seleção antiga. O código 4.1 resolve pelo id da
  sessão, e a divergência vai para o log.

## 4.2.1 Default de LLM do tenant — `tenant_llm_config` (novo, 2026-09-27)

- **Tabela** (módulo `tenant`, migration `tenant_db/V3__tenant_llm_config.sql`, history
  `flyway_schema_history_tenant`): `id`, `create_at`, `update_at`, `tenant_id UUID NOT NULL UNIQUE`,
  `provider VARCHAR(32) NOT NULL`, `model VARCHAR(255) NOT NULL`, FK `tenant_id → tenant(id)`.
  `provider` e `model` têm `CHECK` de allowlist e de não-branco, respectivamente.
- **Por que a FK para `tenant(id)` é segura AQUI**: esta tabela e `tenant` vivem no **mesmo** histórico
  Flyway, logo a ordem de criação é garantida. O módulo `agent` deliberadamente **não** usa FK
  cross-module (mesmo comentário na sua V9): historiales independentes e sem ordem garantida. É essa
  razão que coloca a configuração de LLM no módulo `tenant` e não no `agent`.
- **RN-TC1 (allowlist, não negação)**: `TenantLlmConfigProviders.ALLOWED` (Set imutável) + `CHECK`
  no banco. Um provider novo entra por **decisão explícita** nas duas camadas; uma denylist falha
  aberta e o próximo membro adicionado ao enum seria aceito no dia em que compila, sem ninguém ter
  decidido nada. Espelha a política de `PROVIDERS_WITH_TENANT_BASE_URL` no frontend.
- **RN-TC2 (sem fallback)**: `TenantLlmConfigService.require` lança, não devolve default. Ausente →
  422 `TENANT_LLM_CONFIG_NOT_FOUND` com mensagem que **nomeia a tela** ("Configure em Configurações >
  Provedores"). Um default implícito esconderia a configuração ausente atrás de um comportamento que
  "funciona" e não é diagnosticável.
- **RN-TC3 (isolamento)**: `AbstractEntity` (filtro `tenantFilter` + `TenantListener`) **e** todo finder
  do repositório exige `tenantId` explicitamente. O filtro é defesa em profundidade, não o contrato.
- **RN-TC4 (upsert)**: `PUT /v1/tenant/llm-config` cria ou substitui a linha única. `UNIQUE(tenant_id)`
  serializa duas requisições simultâneas; a violação vira 409 `CONCURRENT_LLM_CONFIG_UPDATE`, nunca o
  nome da constraint.
- **Contratos**: `GET /v1/tenant/llm-config` → `200` com a config ou `200` corpo vazio (não 404:
  "não configurado" é estado normal da tela) · `PUT` → `200`. Ambos `@HasPermission("AGENT_MANAGE")`
  (nome do permiso herdado do editor de agente; renomear é batch próprio — ver dívida) + `@Valid` +
  tenant vindo do **principal autenticado**, nunca do path nem do body.
- **Frontend**: `views/admin/LlmConfigView.vue` na rota `/settings/llm`, ao lado das credenciais de
  provedor. Escolher o vendor reseta o modelo para o default daquele vendor, porque o valor anterior
  quase nunca está no catálogo novo e o servidor o rejeitaria com um erro sobre um modelo que o
  usuário não digitou ali.

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
- **Validate**: `POST /v1/providers/validate {provider, model, baseUrl?}` (era `/v1/agents/validate`; o probe pergunta a um **provedor** se um modelo responde, então foi para a rota de provedor quando o controller de agente saiu) → `200 {ok, provider, model, latencyMs, reason?}`; usa a chave do tenant (sem `apiKey` no body); nunca persiste; falha do vendor → `ok:false` (sem repassar status do vendor); timeout 5s; throttling por tenant.
- **Resolução de chave (chat)**: credential do tenant → fallback env → `422 CONFIG_MISSING` (Ollama: sem chave). Chave ausente nunca vira 500.
- **Migrações**: V9 cria `provider_credential` + V10 migra dados legados (`UPDATE` por `llm_model`, idempotente; `OPENCODE→OPENAI_COMPATIBLE` + baseUrl gateway); colunas `provider_type`/`llm_model` reaproveitadas (zero DDL estrutural em `agent`); `temperature/topP/maxTokens/tools/KB/isDefault` preservados.
- **Blast radius**: breaking change — release único BE+FE; chave é global por tenant (rotação central; override por agente = evolução futura).
- **OUT de escopo**: cotas/limites de uso por tenant (P2 roadmap); vendors nativos além do MVP (fase 2 via compatible).

## 4.4 ~~Base de Conhecimento / RAG — pgvector, módulo `knowledge/`~~ (ADR-010 — 2026-09-25, **supersedido** 2026-09-27)

> ### ⚠️ SUPERSEDIDO — registro histórico, não especificação vigente
>
> O ADR-010 foi supersedido pela decisão `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`
> (Brain). O texto de RN-KB1..RN-KB7 e dos contratos HTTP abaixo é **o que foi removido**, preservado
> para não se repetir o raciocínio — não algo para implementar.
>
> **Esta seção descreve uma funcionalidade que não existe mais em código.** O módulo `knowledge/`, o
> pipeline de RAG do `agent/impl` e os endpoints de vínculo agente↔KB foram **removidos** (Batch 1 de
> 3) para que o produto volte a ser um Assistant simples: caixa de texto, conversa, prompt escolhido.
> Decisão registrada em `arquitetura/projetos/hive/decisoes/scope-assistant-sem-rag`.
>
> **O que ainda é verdade:** as tabelas continuam no banco, órfãs e intactas —
> `knowledge_base`, `kb_document`, `agent_knowledge_base`, `kb_embeddings_768`, `kb_embeddings_1536`
> e a history `flyway_schema_history_knowledge`. **Nenhuma migration de drop foi criada.** Remover
> código não apaga dado; reaplicar o módulo antigo os reutiliza.
>
> **O que não é mais verdade:** os contratos HTTP listados abaixo não existem; os RN-KB1..RN-KB7 não
> são aplicados; `ChatResponse` não devolve `rag_used`/`rag_reason`/`citations`; o trace do chat não
> tem linha de recuperação. O texto abaixo é preservado como **registro do que foi removido** — não
> como especificação vigente.
>
> **Escopo da remoção, Batch 1 (concluído):** o que impedia o `agent` de compilar sem o `knowledge`.
>
> **Batch 2 (concluído, 2026-09-27):** o conceito de **Agent** morreu por completo — entidade,
> serviço, controller, mappers, DTOs, tabela `agent` (`DROP TABLE`, migration `agent/V16`) e
> `chat_session.agent_id`. A sessão aponta para `prompt_template_id` (migration `agent/V15`, que
> **preserva** o dado antigo em `agent_id_legacy` em vez de apagar — ver 4.2.2) e o modelo virou
> `tenant_llm_config` (4.2.1). O `AgentDeleteCascadeIT` e o `ProviderMigrationIT` morreram com as
> tabelas que testavam; a cobertura que sobreviveu (409 de prompt em uso, anti-IDOR 404) foi
> trazida para `ChatSessionIT`.


- **Bounded context**: módulo `knowledge/` (`api/impl/dbupdate/web/internal/v1`); KBs, documentos, chunks/embeddings, vínculo agente↔KB, playground. `agent/` consome via `knowledge/api` (retrieval no chat).
- **RN-KB1 (ownership)**: toda KB e documento pertencem a um tenant (`tenant_id NOT NULL`); cross-tenant → 404 uniforme (anti-IDOR).
- **RN-KB2 (papéis)**: `KNOWLEDGE_MANAGE` (CRUD, upload, URL, reindex, vínculo) e `KNOWLEDGE_READ` (leitura + playground); chat com RAG exige vínculo do agente (só `KNOWLEDGE_READ` não autoriza retrieval arbitrário). **⚠️ Ambas removidas em 2026-09-27:** nenhuma era verificada por endpoint desde a queda do `KnowledgeBaseControllerV1`, e `SecurityTokenServiceImpl` continuava concedendo as duas — todo token carregava 2 permissões que não concediam nada, o que engana quem audita as permissões de um usuário. As constantes e as entradas da lista de concessão saíram juntas.
- **RN-KB3 (limites MVP)**: arquivo máx. 10MB (request 12MB; >10MB → 413); tipos PDF/TXT/MD com magic-bytes (fora → 400); URL máx. 2MB via stream; PDF escaneado (imagem) não suportado; chunking recursive 512 tokens/overlap 50.
- **RN-KB4 (ciclo de vida)**: documento `PENDING → INDEXING → READY | FAILED` (erro sem stacktrace); reindex idempotente (`removeAll(kb_id)` + reingestão); URL é snapshot único (sem recrawl).
- **RN-KB5 (N:N max 5)**: junção `agent_knowledge_base`; máx. 5 KBs/agente (6ª → 400); KBs vinculadas exigem mesma dimensão (mix 1536+768 → 400 com mensagem PT-BR no picker).
- **RN-KB6 (delete)**: delete com vínculo ativo → 409 `KB_IN_USE` + lista de agentes; `?force=true` desvincula + cascade (204).
- **RN-KB7 (RAG só com fonte)**: retrieval `top 5, minScore 0.7, timeout 2s` (fallback: segue sem contexto); sem chunk ≥ threshold → `rag_used:false`, citações vazias; resposta com `rag_used + citations[{docId,docName,chunkId,score}]`; contexto delimitado anti-prompt-injection.
- **Contratos**: `POST /v1/knowledge-bases 201` · `GET /v1/knowledge-bases` (paginado) · `POST /{id}/documents:upload 202` (multipart) · `POST /{id}/documents:url 202` (SSRF guard) · `GET /{id}/documents` · `POST /{id}/reindex 202` · `DELETE /{id} 204/409` · `POST /{id}/search {query,topK} 200` (playground, mesma função do chat) · `PATCH /v1/agents/{id} {knowledgeBaseIds}` (vínculo) · `GET /v1/agents/{agentId}/knowledge-bases`.
- **Playground (FE)**: aba Testar na edição da base — Textarea + Slider topK 1–10 (default 5) + chunk cards com score badge (≥0.90 alta, ≥0.70 média) e fonte arquivo; picker de KBs no editor de agente (max 5 + contador, mix de dimensões bloqueado).
- **Segurança**: SSRF guard dedicado (loopback/CGNAT bloqueados, `followRedirects NEVER` + revalidação ≤3 hops, anti-rebinding, portas 80/443, timeouts 5s/10s); `WHERE tenant_id` explícito em toda query vetorial nativa; async sempre com `TenantContext.runWithTenant`; ingestão com rate limit por tenant; embeddings com credencial do tenant (sem env global).
- **Persistência**: `knowledge_base(UNIQUE tenant,slug; status ACTIVE|ARCHIVED)` + `kb_document(status, sha256 dedup, error)` + `agent_knowledge_base` (V11) + `kb_embeddings_{1536,768}` JSONB + índices vetoriais (V12); backfill `Agent.knowledgeBases TEXT[]` → KBs placeholder (V13); `Flyway` history própria `flyway_schema_history_knowledge`.

## 4.2.2 `chat_session.agent_id` → `prompt_template_id` — a decisão irreversível (2026-09-27)

**O risco.** Um `DROP COLUMN agent_id` + `ADD prompt_template_id` destrói, em silêncio, a associação
de toda sessão existente em qualquer ambiente com histórico: toda sessão antiga fica sem prompt e o
chat não tem para onde enviar a mensagem. Perda de dado real, invisível no diff, irreversível.

**A estratégia implementada** (`agent/V15__chat_session_prompt_template.sql`, uma transação):

1. `ADD COLUMN prompt_template_id` (nullable) — nada foi destruído ainda.
2. **Backfill** a partir de `agent.prompt_template_id`. A associação é preservada **por dado**, não
   por cópia manual: toda sessão cujo agente tinha template herda o mesmo template.
3. **Guard**: se sobrar sessão órfã (agente sem template), a migration **aborta** com `RAISE EXCEPTION`
   contando as órfãs e dando o SQL exato de remediação. O Flyway executa cada migration PostgreSQL
   numa transação, então o rollback é inteiro — **não existe estado meio-aplicado e nenhum dado foi
   perdido**. Abortar é reversível; seguir com `NULL` não é.
4. Só então a FK `fk_chat_session_agent` cai e a coluna é **RENOMEADA** para `agent_id_legacy` — não
   apagada. `agent_id` deixa de existir como coluna, o código para de lê-la, e a reversão é um
   `ALTER TABLE chat_session RENAME COLUMN agent_id_legacy TO agent_id`.
5. `prompt_template_id` vira `NOT NULL` com FK `ON DELETE RESTRICT` para `prompt_template`, e ganha
   índice.
6. `agent/V16__drop_agent.sql` apaga a tabela `agent` (RESTRICT implícito: se sobrar referência de
   qualquer tabela viva, o startup falha em vez de derrubar dado) e imprime quantas sessões
   ficaram com `agent_id_legacy` preservado.

**Reversibilidade.** Sim, e é o que foi implementado. Renomear é reversível; apagar não seria. A
remoção física de `agent_id_legacy` é **decisão de batch próprio**, a ser tomada com o número real de
sessões preservadas em mãos — a V16 imprime esse número.

**A coluna antiga saiu do caminho do código, não do banco.** Ela não é lida, não é FK e não aparece em
nenhuma query. Isso satisfaz "a coluna vai" sem destruir o dado.
