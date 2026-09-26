# Hive AI — Development Roadmap

> **Last updated**: 2026-08-26
> **Architecture**: Modular monolith — Java 21, Spring Boot 4.1.0, PostgreSQL 16 + pgvector, Redis, RabbitMQ, LangChain4j, Vue 3.5, PrimeVue 4

---

## Current State Summary

| Layer | Status | Notes |
|-------|--------|-------|
| **Agent CRUD** | Working | `AgentService` + `AgentRepository` + `AgentControllerV1` |
| **Prompt Versioning** | Working | V3 migration — code+version+status, activate/archive |
| **Chat** | **STATELESS** | `ChatServiceImpl.chat()` — takes message → calls LLM → returns. No persistence. |
| **Chat Entities** | Created, **NOT wired** | `ChatSessionEntity` + `ChatMessageEntity` exist (V4). No repositories. |
| **Provider: Ollama** | Working | LangChain4j adapter functional |
| **Provider: OpenAI** | Broken | Throws exceptions |
| **Provider: Gemini** | Broken | Throws exceptions |
| **Frontend Chat** | Partial | `Chat.vue` works but agentId hardcoded, no session/history |
| **Frontend Sidebar** | Placeholder | Hardcoded items, not connected to API |

### Key Gaps

- `ChatServiceImpl` ignores `sessionId` — always creates new random UUID
- No repositories for `ChatSessionEntity` or `ChatMessageEntity`
- No `ChatSession` / `ChatMessage` domain VOs
- `ChatRequest` has `sessionId` field but it's unused in the flow
- Frontend sends hardcoded `agentId: '00000000-0000-0000-0000-000000000000'`
- No multi-turn context (history not sent to LLM)

---

## Phase 0 — MVP Chat (Critical)

**Goal**: Chat works end-to-end with persistence and multi-turn context.

**Business Value**: Without persistence, every page reload loses the conversation. Multi-turn context is the baseline expectation for any chat product. This phase makes Hive usable.

### Tasks

#### Backend

- [ ] Create `ChatSessionRepository` extends `JpaRepository<ChatSessionEntity, UUID>`
  - File: `agent/impl/src/main/java/.../repository/ChatSessionRepository.java`
  - Methods: `findByUserIdAndTenantId(UUID userId, UUID tenantId, Pageable)`, `findByIdAndTenantId(UUID, UUID)`
- [ ] Create `ChatMessageRepository` extends `JpaRepository<ChatMessageEntity, UUID>`
  - File: `agent/impl/src/main/java/.../repository/ChatMessageRepository.java`
  - Methods: `findBySessionIdOrderByCreateAtAsc(UUID sessionId)`
- [ ] Create domain VOs: `ChatSession` (id, userId, agentId, title, summary, createAt, updateAt) and extend `ChatMessage` with `sessionId`
  - File: `agent/api/src/main/java/.../domain/ChatSession.java`
- [ ] Create `ChatSessionMapper` (MapStruct) — domain ↔ entity
  - File: `agent/impl/src/main/java/.../mapper/ChatSessionMapperDomain.java`
- [ ] Create `ChatMessageMapper` (MapStruct) — domain ↔ entity
  - File: `agent/impl/src/main/java/.../mapper/ChatMessageMapperDomain.java`
- [ ] Create `ChatSessionService` interface + `ChatSessionServiceImpl`
  - File: `agent/api/src/main/java/.../service/ChatSessionService.java`
  - File: `agent/impl/src/main/java/.../service/ChatSessionServiceImpl.java`
  - Methods: `createSession(userId, agentId, title)`, `listSessions(userId, pageable)`, `getSession(id)`, `deleteSession(id)`, `getMessages(sessionId)`
- [ ] Modify `ChatServiceImpl.chat()` to:
  1. If `request.getSessionId()` provided → load existing session + history
  2. If no session → create new session (title = first 50 chars of message)
  3. Save user message to `ChatMessageEntity`
  4. Load full history → map to `ChatMessageProvider` list → send to LLM
  5. Save assistant response to `ChatMessageEntity`
  6. Update session `updateAt` timestamp
- [ ] Create `ChatSessionControllerV1`
  - File: `agent/web/internal/v1/src/main/java/.../controller/ChatSessionControllerV1.java`
  - Endpoints:
    - `POST /v1/chat/sessions` — create session
    - `GET /v1/chat/sessions` — list sessions (paginated, current user)
    - `GET /v1/chat/sessions/{id}` — get session detail
    - `DELETE /v1/chat/sessions/{id}` — soft/hard delete session + messages
    - `GET /v1/chat/sessions/{id}/messages` — list messages for session
- [ ] Create DTOs: `ChatSessionRequestDTO`, `ChatSessionResponseDTO`, `ChatMessageResponseDTO`
  - File: `agent/web/internal/v1/src/main/java/.../dto/ChatSessionRequestDTO.java`
  - File: `agent/web/internal/v1/src/main/java/.../dto/ChatSessionResponseDTO.java`
  - File: `agent/web/internal/v1/src/main/java/.../dto/ChatMessageResponseDTO.java`
- [ ] Update `ChatMapper` to handle `sessionId` in request/response
- [ ] Update `ChatRequest` to ensure `agentId` is properly resolved (not hardcoded)

#### Frontend

- [ ] Create `session.service.ts` — API layer for session CRUD
  - File: `src/services/session.service.ts`
  - Methods: `createSession(agentId, title)`, `listSessions(page)`, `getSession(id)`, `deleteSession(id)`, `getMessages(sessionId)`
- [ ] Modify `ChatSidebar.vue`:
  - Fetch sessions from API on mount
  - Render real session list (title + date)
  - Click session → emit `selectSession(sessionId)`
  - "Nova Conversa" button → create session + clear messages
  - Delete session (swipe or context menu)
- [ ] Modify `Chat.vue`:
  - Accept `sessionId` prop or route param
  - On session select → load messages from API → display
  - On send message → include `sessionId` in request
  - On first message → auto-create session, update URL
  - Agent selection dropdown (fetch agents from `/v1/agents`)
- [ ] Create `AgentSelector.vue` component
  - File: `src/components/AgentSelector.vue`
  - Dropdown populated from `/v1/agents` endpoint
  - Store selected agent in session
- [ ] Update `auth.store.ts` to expose `userId` for session scoping

#### Database

- No new migrations needed — V4 already created `chat_session` and `chat_message` tables with correct schema.

#### Unit Tests

- [ ] `ChatSessionServiceImplTest` — create, list, delete, getMessages
- [ ] `ChatServiceImplTest` — update existing tests for persistence flow, add multi-turn test
- [ ] `ChatSessionControllerV1Test` (MockMvc integration) — all endpoints

#### Acceptance Criteria

1. Send a message → creates a session (title = first 50 chars) → message persisted in DB
2. Send second message in same session → both messages in history → LLM receives full context
3. Refresh page → sidebar shows real sessions → click loads full history
4. Create "Nova Conversa" → clears messages, creates new session
5. Delete session → removes from sidebar and DB
6. Agent selector shows real agents from API (not hardcoded)
7. All unit tests pass, `mvn clean install` green

#### Dependencies

- None — greenfield from current state.

#### Estimated Effort: **L** (2-3 weeks)

---

## Phase 1 — Agent Management API

**Goal**: Full CRUD API for agents and prompts with proper auth. Admin panel in frontend.

**Business Value**: External teams can programmatically manage agents without touching code. Foundation for API-first design.

### Tasks

#### Backend

- [ ] Review existing `AgentControllerV1` — add missing endpoints:
  - `GET /v1/agents` — list all agents (paginated, filtered by tenant)
  - `GET /v1/agents/{id}` — get agent detail
  - `POST /v1/agents` — create agent
  - `PUT /v1/agents/{id}` — update agent
  - `DELETE /v1/agents/{id}` — soft delete (set `active=false`)
- [ ] Add `@HasPermission("AGENT_MANAGE")` to all agent endpoints
- [ ] Review existing `PromptTemplateControllerV1` — ensure endpoints:
  - `POST /v1/prompts` — create prompt template draft
  - `PUT /v1/prompts/{id}/activate` — activate a version
  - `PUT /v1/prompts/{id}/archive` — archive a version
  - `GET /v1/prompts` — list all templates
  - `GET /v1/prompts/{id}/versions` — list versions
- [ ] Add `@HasPermission("PROMPT_MANAGE")` to all prompt endpoints
- [ ] Add pagination support (`Pageable`) to list endpoints
- [ ] Add OpenAPI/Swagger annotations (`@Operation`, `@ApiResponse`) to all endpoints

#### Frontend

- [ ] Create admin views:
  - `src/views/admin/AgentList.vue` — table with search, create, edit, delete
  - `src/views/admin/AgentDetail.vue` — form: name, description, prompt, model, provider, temperature
  - `src/views/admin/PromptList.vue` — template list with version management
  - `src/views/admin/PromptDetail.vue` — version editor with activate/archive
- [ ] Create admin routes in `router/index.ts`:
  - `/admin/agents` → `AgentList.vue`
  - `/admin/agents/:id` → `AgentDetail.vue`
  - `/admin/prompts` → `PromptList.vue`
  - `/admin/prompts/:id` → `PromptDetail.vue`
- [ ] Add admin navigation to sidebar (visible only with `AGENT_MANAGE` permission)
- [ ] Create `agent.service.ts` and `prompt.service.ts` — API layers
- [ ] Guard admin routes with permission check (redirect if unauthorized)

#### Database

- No new migrations — existing schema supports all operations.

#### Unit Tests

- [ ] `AgentControllerV1IT` — all CRUD endpoints, auth, validation
- [ ] `PromptTemplateControllerV1IT` — all endpoints
- [ ] Update `AgentServiceImplTest` for edge cases (not found, validation)

#### Acceptance Criteria

1. Full CRUD for agents via REST API with pagination
2. Full CRUD for prompts with version management (create → activate → archive)
3. All endpoints protected with `@HasPermission`
4. Admin UI: create/edit/delete agents, manage prompt versions
5. Non-admin users see no admin menu items
6. Swagger UI shows all endpoints at `/v1/swagger-ui`

#### Dependencies

- Phase 0 complete (chat works with agent selection)

#### Estimated Effort: **M** (1-2 weeks)

---

## Phase 2 — Knowledge Base + RAG

**Goal**: External systems can upload documents. Agents can retrieve context from knowledge bases during chat.

**Business Value**: Without RAG, agents only know their system prompt. Knowledge bases let agents answer questions about specific documents, codebases, or domain knowledge.

### Tasks

#### Backend

- [ ] Create new Maven module: `knowledge` (api/impl/dbupdate/web)
  - Module structure follows existing pattern (tenant/commons pattern)
- [ ] Create `knowledge` Flyway migration (V2):
  ```sql
  CREATE TABLE knowledge_base (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      name VARCHAR(255) NOT NULL,
      description TEXT,
      tenant_id UUID NOT NULL,
      create_at TIMESTAMP,
      update_at TIMESTAMP
  );
  CREATE TABLE knowledge_document (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      knowledge_base_id UUID NOT NULL REFERENCES knowledge_base(id),
      filename VARCHAR(500),
      content_type VARCHAR(100),
      file_size BIGINT,
      status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, PROCESSING, READY, FAILED
      tenant_id UUID NOT NULL,
      create_at TIMESTAMP,
      update_at TIMESTAMP
  );
  CREATE TABLE knowledge_chunk (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      document_id UUID NOT NULL REFERENCES knowledge_document(id),
      content TEXT NOT NULL,
      embedding VECTOR(1536), -- OpenAI ada-002 dimension, configurable per model
      chunk_index INTEGER,
      token_count INTEGER,
      tenant_id UUID NOT NULL,
      create_at TIMESTAMP
  );
  CREATE INDEX idx_chunk_embedding ON knowledge_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
  ```
- [ ] Create `KnowledgeBaseService` + `KnowledgeDocumentService`
  - CRUD for knowledge bases
  - Document upload → chunking → embedding pipeline
- [ ] Implement chunking strategy:
  - 512 tokens per chunk, 50 token overlap
  - Split by paragraph/sentence boundaries when possible
  - Use LangChain4j `DocumentSplitter` or custom implementation
- [ ] Implement embedding pipeline:
  - Use existing provider abstraction for embedding models
  - Store vectors in pgvector column
  - Batch embedding calls (100 chunks per batch)
- [ ] Implement RAG search:
  - `search(knowledgeBaseId, query, topK)` → embed query → cosine similarity search → return top-K chunks
  - pgvector query: `SELECT * FROM knowledge_chunk WHERE knowledge_base_id = ? ORDER BY embedding <=> ? LIMIT ?`
- [ ] Modify `ChatServiceImpl` to integrate RAG:
  - If agent has associated knowledge bases → search relevant chunks
  - Augment system prompt with retrieved context
  - Send augmented prompt to LLM
- [ ] Create REST API:
  - `POST /v1/knowledge-bases` — create KB
  - `GET /v1/knowledge-bases` — list KBs
  - `GET /v1/knowledge-bases/{id}` — get KB detail
  - `DELETE /v1/knowledge-bases/{id}` — delete KB + all documents
  - `POST /v1/knowledge-bases/{id}/documents` — upload document (multipart)
  - `GET /v1/knowledge-bases/{id}/documents` — list documents
  - `DELETE /v1/knowledge-bases/{id}/documents/{docId}` — delete document
  - `POST /v1/knowledge-bases/{id}/search` — search (test endpoint)
- [ ] Add `knowledge_base_id` column to `agent` table (migration V5) — links agent to KB
- [ ] Async document processing via RabbitMQ (document upload → message → worker processes chunks)

#### Frontend

- [ ] Create knowledge base views:
  - `src/views/admin/KnowledgeBaseList.vue` — list KBs with document count
  - `src/views/admin/KnowledgeBaseDetail.vue` — upload documents, view chunks, search test
- [ ] Create KB management routes under `/admin/knowledge/`
- [ ] Add KB association to agent editor (Phase 1) — multi-select knowledge bases
- [ ] Create `knowledge.service.ts` — API layer

#### Database

- [ ] V5: `ALTER TABLE agent ADD COLUMN knowledge_base_id UUID REFERENCES knowledge_base(id)`
- [ ] V2 for knowledge module (tables above)

#### Unit Tests

- [ ] `KnowledgeBaseServiceImplTest` — CRUD operations
- [ ] `KnowledgeDocumentServiceImplTest` — upload, chunking, status updates
- [ ] `RagSearchServiceTest` — embedding + similarity search
- [ ] `ChatServiceImplTest` — RAG augmentation integration

#### Acceptance Criteria

1. Upload a PDF/TXT/MD → document chunked → embeddings stored in pgvector
2. Search KB → returns relevant chunks ranked by similarity
3. Chat with agent linked to KB → agent answers using document context
4. KB management UI: create, upload, view documents, delete
5. Document processing handles errors gracefully (status = FAILED with error message)
6. Unit + integration tests pass

#### Dependencies

- Phase 0 (chat with session management)
- pgvector extension enabled on PostgreSQL

#### Estimated Effort: **XL** (3-4 weeks)

---

## Phase 3 — API-First External Integration

**Goal**: Third-party systems can authenticate with API keys and use all platform features programmatically.

**Business Value**: Enables ecosystem growth — partners, integrations, automation. Revenue opportunity via API access tiers.

### Tasks

#### Backend

- [ ] Create `api-key` module (or add to `security` module)
- [ ] Create `api_key` table migration:
  ```sql
  CREATE TABLE api_key (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      name VARCHAR(255) NOT NULL,
      key_hash VARCHAR(255) NOT NULL, -- SHA-256 hash
      key_prefix VARCHAR(10) NOT NULL, -- 'hk_' + first 6 chars for identification
      tenant_id UUID NOT NULL,
      permissions JSONB,
      rate_limit INTEGER DEFAULT 100, -- requests per minute
      expires_at TIMESTAMP,
      last_used_at TIMESTAMP,
      active BOOLEAN DEFAULT TRUE,
      create_at TIMESTAMP,
      update_at TIMESTAMP
  );
  CREATE UNIQUE INDEX idx_api_key_hash ON api_key(key_hash);
  ```
- [ ] Create `ApiKeyService`:
  - `generateKey(name, tenantId, permissions, rateLimit, expiresAt)` → returns plaintext key (shown once)
  - `validateKey(keyHash)` → returns ApiKey domain if valid + not expired + active
  - `revokeKey(id)` → soft delete
  - `listKeys(tenantId)` → list (without plaintext)
- [ ] Create API key auth filter:
  - Extract `Authorization: Bearer hk_...` header
  - Hash key → lookup in DB → inject tenant + permissions into security context
  - Integrate with Spring Security filter chain
- [ ] Rate limiting via Redis:
  - Key: `rate_limit:{api_key_id}:{window}`
  - Sliding window counter (Redis sorted set)
  - Return `429 Too Many Requests` with `X-RateLimit-*` headers
- [ ] Create REST API:
  - `POST /v1/api-keys` — generate key (returns plaintext once)
  - `GET /v1/api-keys` — list keys (no plaintext)
  - `DELETE /v1/api-keys/{id}` — revoke key
- [ ] Webhook system:
  - Create `webhook` table (url, events, secret, active)
  - Event emitter on: `message.created`, `session.created`, `agent.updated`
  - Sign payloads with HMAC-SHA256 using webhook secret
  - Async delivery via RabbitMQ with retry
- [ ] OpenAPI/Swagger:
  - Enable Swagger UI at `/v1/swagger-ui` (protected by auth)
  - Export OpenAPI spec at `/v1/api-docs`
  - Group endpoints by tag (Chat, Agent, Prompt, Knowledge, API Key)

#### Frontend

- [ ] API key management view:
  - `src/views/admin/ApiKeyList.vue` — list keys, generate, revoke
  - Show key prefix (never full key after creation)
  - Copy-to-clipboard for newly generated key
- [ ] Webhook management view:
  - `src/views/admin/WebhookList.vue` — CRUD webhooks, select events
- [ ] API documentation page (embed Swagger UI or link to it)

#### Database

- [ ] V6 (agent module): `api_key` table
- [ ] V7 (agent module): `webhook` table

#### Unit Tests

- [ ] `ApiKeyServiceTest` — generate, validate, revoke, expiry
- [ ] `RateLimitFilterTest` — rate limiting logic
- [ ] `WebhookServiceTest` — event registration, payload signing

#### Acceptance Criteria

1. Generate API key → receive plaintext key (shown once) → prefix `hk_`
2. Use key in `Authorization: Bearer hk_...` header → authenticated as tenant
3. Rate limit: exceed limit → 429 with proper headers
4. Revoke key → subsequent requests fail with 401
5. Webhook: register for `message.created` → receive signed POST when message sent
6. Swagger UI accessible at `/v1/swagger-ui` with full API documentation
7. All endpoints documented with OpenAPI annotations

#### Dependencies

- Phase 1 (agent management endpoints exist)
- Redis available for rate limiting

#### Estimated Effort: **L** (2-3 weeks)

---

## Phase 4 — MCP Integration

**Goal**: Model Context Protocol support — expose Hive tools externally and consume external MCP servers.

**Business Value**: MCP is emerging as the standard for AI tool interoperability. Enables agents to call external tools (Google, GitHub, databases) and external systems to use Hive's tools.

### Tasks

#### Backend

- [ ] Add Spring AI MCP SDK dependency (or implement custom MCP server)
- [ ] MCP Server — expose Hive tools:
  - `create_agent` — create agent programmatically
  - `search_knowledge` — search knowledge base
  - `send_message` — send message to an agent
  - `list_agents` — list available agents
  - Register tools via MCP protocol (JSON-RPC over stdio or SSE)
- [ ] MCP Client — connect to external servers:
  - Configure external MCP servers (Google, GitHub, etc.) via admin config
  - Discovery: list available tools from external servers
  - Execution: agent calls external tool → MCP client routes request → return result
- [ ] Tool calling in chat:
  - Modify `ChatServiceImpl` to support tool calling mode
  - LLM returns tool_call → execute tool → feed result back → continue conversation
  - Support multi-step tool chains (up to 5 iterations)
- [ ] Create `McpServerConfig` entity + service:
  - Store MCP server configurations (URL, type, auth, status)
  - Health check for connected servers
- [ ] REST API:
  - `GET /v1/mcp/servers` — list configured MCP servers
  - `POST /v1/mcp/servers` — register MCP server
  - `DELETE /v1/mcp/servers/{id}` — remove server
  - `GET /v1/mcp/tools` — list all available tools (Hive + external)
  - `POST /v1/mcp/tools/{toolId}/execute` — execute tool directly (test endpoint)

#### Frontend

- [ ] MCP server management view:
  - `src/views/admin/McpServerList.vue` — list connected servers, health status
  - `src/views/admin/McpServerDetail.vue` — configure server connection
- [ ] Tool configuration:
  - `src/views/admin/ToolList.vue` — list available tools (Hive + external)
  - Allow agents to be configured with allowed tools
- [ ] Chat tool call visualization:
  - In `ChatMessage.vue` — show tool calls inline (tool name, input, output)
  - Collapsible sections for tool call details
  - Status indicators (pending, success, error)

#### Database

- [ ] V8 (agent module):
  ```sql
  CREATE TABLE mcp_server (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      name VARCHAR(255) NOT NULL,
      url VARCHAR(500),
      server_type VARCHAR(20) NOT NULL, -- LOCAL, REMOTE
      transport VARCHAR(20) DEFAULT 'SSE', -- SSE, STDIO
      auth_config JSONB,
      status VARCHAR(20) DEFAULT 'DISCONNECTED', -- CONNECTED, DISCONNECTED, ERROR
      tenant_id UUID NOT NULL,
      create_at TIMESTAMP,
      update_at TIMESTAMP
  );
  CREATE TABLE agent_tool (
      agent_id UUID NOT NULL REFERENCES agent(id),
      tool_name VARCHAR(255) NOT NULL,
      mcp_server_id UUID REFERENCES mcp_server(id),
      enabled BOOLEAN DEFAULT TRUE,
      PRIMARY KEY (agent_id, tool_name)
  );
  ```

#### Unit Tests

- [ ] `McpServerServiceTest` — CRUD, health check
- [ ] `ToolExecutionServiceTest` — tool routing, error handling
- [ ] `ChatServiceImplToolCallTest` — multi-step tool calling flow

#### Acceptance Criteria

1. Hive exposes tools via MCP protocol — external clients can discover and call them
2. Connect to external MCP server → tools appear in tool list
3. Agent configured with tools → chat can invoke tools → results shown in UI
4. Tool call visualization shows name, input, output, status
5. Tool execution handles timeouts and errors gracefully
6. Max 5 tool call iterations per turn (prevents infinite loops)

#### Dependencies

- Phase 0 (chat working)
- Phase 2 (knowledge base for `search_knowledge` tool)
- Spring AI MCP SDK availability

#### Estimated Effort: **XL** (3-4 weeks)

---

## Phase 5 — Streaming + Polish

**Goal**: Production-ready chat experience with streaming responses, rich content rendering, and file upload.

**Business Value**: Streaming eliminates perceived latency. Rich content (markdown, code, files) makes responses useful. This is the UX that differentiates from basic chatbots.

### Tasks

#### Backend

- [ ] Implement SSE streaming in `ChatServiceImpl`:
  - Use Spring `SseEmitter` or Reactor `Flux<ServerSentEvent>`
  - Stream tokens as they arrive from LLM provider
  - Endpoint: `POST /v1/chat/stream` — returns `text/event-stream`
  - Support both streaming and non-streaming modes (controlled by `stream` flag in `ChatRequest`)
- [ ] Modify `AiProvider` interface to support streaming:
  - Add `chatStream(ChatRequestProvider)` → `Flux<String>` or callback-based
  - Update LangChain4j provider to use streaming API
- [ ] File upload endpoint:
  - `POST /v1/chat/sessions/{sessionId}/attachments` — multipart upload
  - Store in object storage (S3/MinIO) or local filesystem
  - Return file URL for inclusion in message
  - Support: images (png, jpg, gif), documents (pdf, txt, md, csv)
- [ ] Token usage tracking:
  - Store `input_tokens` and `output_tokens` in message metadata
  - Aggregate per session, per agent for monitoring

#### Frontend

- [ ] Streaming message rendering:
  - Connect to SSE endpoint via `EventSource` or `fetch` with `ReadableStream`
  - Typewriter effect: append tokens to message as they arrive
  - Show cursor indicator while streaming
  - Handle stream interruption (reconnect or fallback)
- [ ] Markdown rendering:
  - Integrate `markdown-it` or `marked` library
  - Render assistant messages as markdown (headers, lists, bold, italic, links)
  - Security: sanitize HTML output (DOMPurify)
- [ ] Code syntax highlighting:
  - Integrate `highlight.js` or `prismjs`
  - Render fenced code blocks with language detection
  - Add copy-to-clipboard button on code blocks
- [ ] File upload in chat:
  - Drag-and-drop zone on chat input area
  - File picker button
  - Upload progress indicator
  - Preview images inline, link to documents
  - Include file reference in message payload
- [ ] Suggestion chips:
  - After assistant response, show 2-3 suggested follow-up questions
  - Generated by LLM based on conversation context
  - Click to send as user message
- [ ] Message actions:
  - Copy message content
  - Regenerate response (re-send last user message)
  - Edit and resend user message

#### Database

- [ ] V9 (agent module):
  ```sql
  ALTER TABLE chat_message ADD COLUMN token_input INTEGER;
  ALTER TABLE chat_message ADD COLUMN token_output INTEGER;
  CREATE TABLE chat_attachment (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      message_id UUID REFERENCES chat_message(id),
      filename VARCHAR(500),
      content_type VARCHAR(100),
      file_size BIGINT,
      storage_path VARCHAR(1000),
      tenant_id UUID NOT NULL,
      create_at TIMESTAMP
  );
  ```

#### Unit Tests

- [ ] `ChatServiceImplStreamingTest` — streaming response handling
- [ ] Frontend: `ChatMessage.spec.ts` — markdown rendering, code highlighting
- [ ] Frontend: `Chat.spec.ts` — streaming connection, file upload

#### Acceptance Criteria

1. Send message → response streams token-by-token (typewriter effect)
2. Markdown renders correctly (headers, lists, bold, links)
3. Code blocks syntax-highlighted with copy button
4. Drag-and-drop file → uploads → appears in message
5. Suggestion chips appear after assistant response
6. Copy/regenerate/edit message actions work
7. Stream interruption handled gracefully (retry or show partial response)

#### Dependencies

- Phase 0 (chat with sessions)
- LangChain4j streaming support for provider

#### Estimated Effort: **L** (2-3 weeks)

---

## Phase 6 — Production Hardening

**Goal**: Enterprise-ready platform with monitoring, security hardening, and reliability.

**Business Value**: Production readiness — handles real traffic, fails gracefully, observable, maintainable.

### Tasks

#### Backend

- [ ] Rate limiting (global):
  - Redis-based rate limiting per IP and per API key
  - Configurable limits per endpoint
  - `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers
- [ ] Circuit breaker:
  - Integrate Resilience4j circuit breaker on LLM provider calls
  - Fallback: return cached response or graceful error
  - Configurable thresholds (failure rate, slow call rate)
- [ ] Monitoring & metrics:
  - Micrometer + Prometheus metrics
  - Key metrics: chat latency, LLM provider errors, active sessions, token usage
  - Health checks: DB, Redis, RabbitMQ, LLM providers
  - Actuator endpoints (protected by auth)
- [ ] Structured logging:
  - JSON log format for production
  - Correlation ID per request
  - Log levels: ERROR for failures, WARN for degraded, INFO for operations
- [ ] Backup & restore:
  - Knowledge base document backup strategy
  - Database backup automation
  - Restore procedure documentation
- [ ] Security hardening:
  - CSRF protection review
  - Input sanitization audit
  - Dependency vulnerability scan (OWASP Dependency-Check)
  - SQL injection prevention verification
  - Rate limit on authentication endpoints

#### Frontend

- [ ] Error handling:
  - Global error boundary component
  - Retry logic for failed API calls (exponential backoff)
  - Offline detection and indicator
  - Graceful degradation (show cached data when API fails)
- [ ] Accessibility (WCAG AA):
  - Keyboard navigation for all interactive elements
  - ARIA labels on chat components
  - Screen reader announcements for new messages
  - Color contrast compliance
  - Focus management (input → send → new message)
- [ ] Performance:
  - Lazy load chat history (virtual scrolling for long conversations)
  - Image lazy loading
  - Service worker for offline caching (static assets)
- [ ] Dark mode polish:
  - Verify all components in dark mode
  - System preference detection (`prefers-color-scheme`)
- [ ] Internationalization (i18n):
  - Extract hardcoded strings
  - Set up i18n framework (vue-i18n)
  - Portuguese (default) + English

#### Database

- No new migrations — optimization of existing indexes and queries.

#### Unit Tests

- [ ] Circuit breaker integration test
- [ ] Rate limiting test
- [ ] Frontend accessibility tests (axe-core)
- [ ] Load testing: 100 concurrent chat sessions

#### Acceptance Criteria

1. Rate limiting: exceed limit → 429 with proper headers
2. Circuit breaker: LLM provider down → fallback response within 5s
3. Metrics dashboard shows real-time chat latency, errors, token usage
4. Structured JSON logs with correlation IDs
5. All frontend components keyboard navigable
6. Screen reader can use chat (announces new messages)
7. Dark mode works correctly across all views
8. Zero critical vulnerabilities in dependency scan
9. 100 concurrent sessions handled without degradation

#### Dependencies

- All previous phases complete
- Redis for rate limiting
- Prometheus/Grafana for metrics (or equivalent)

#### Estimated Effort: **L** (2-3 weeks)

---

## Dependency Graph

```
Phase 0 (MVP Chat)
├── Phase 1 (Agent Management)
│   ├── Phase 3 (API-First)
│   └── Phase 4 (MCP) ← also depends on Phase 2
├── Phase 2 (Knowledge Base + RAG)
│   └── Phase 4 (MCP)
└── Phase 5 (Streaming) ← can start after Phase 0, run in parallel
    └── Phase 6 (Production Hardening)
```

### Parallel Tracks

- **Track A**: Phase 0 → Phase 1 → Phase 3 (API-first)
- **Track B**: Phase 0 → Phase 2 (Knowledge Base)
- **Track C**: Phase 0 → Phase 5 (Streaming) — can overlap with Track A/B
- **Track D**: Phase 4 (MCP) — starts after Phase 0+2 complete
- **Track E**: Phase 6 (Production) — final, after all features

---

## Effort Summary

| Phase | Effort | Duration (est.) |
|-------|--------|-----------------|
| Phase 0 — MVP Chat | L | 2-3 weeks |
| Phase 1 — Agent Management | M | 1-2 weeks |
| Phase 2 — Knowledge Base + RAG | XL | 3-4 weeks |
| Phase 3 — API-First Integration | L | 2-3 weeks |
| Phase 4 — MCP Integration | XL | 3-4 weeks |
| Phase 5 — Streaming + Polish | L | 2-3 weeks |
| Phase 6 — Production Hardening | L | 2-3 weeks |
| **Total** | | **~14-20 weeks** |

---

## Technical Decisions (Reference)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Chat persistence | `ChatSession` (1) → `ChatMessage` (N) | Standard conversation model, supports multi-turn |
| Session title | First 50 chars of first message | Quick identification without LLM call |
| Embeddings storage | pgvector in PostgreSQL | No extra infrastructure, cosine similarity native |
| Chunking | 512 tokens, 50 overlap | Balanced context vs retrieval precision |
| API key prefix | `hk_` (hive key) | Visual identification, prevents confusion with other keys |
| Streaming | Spring SseEmitter | Simple, no extra dependencies, Spring native |
| MCP | Spring AI MCP SDK | Official Spring support, reduces custom code |
| Rate limiting | Redis sliding window | Accurate, supports distributed deployment |
| Circuit breaker | Resilience4j | Industry standard for Spring Boot |
| Markdown | markdown-it + highlight.js | Proven, lightweight, extensible |

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| LLM provider instability (OpenAI/Gemini) | Chat failures | Circuit breaker + fallback to Ollama |
| pgvector performance at scale | Slow RAG search | IVFFlat index tuning, consider HNSW for >1M chunks |
| MCP protocol immaturity | Breaking changes | Abstract MCP layer, isolate from core chat |
| File storage growth | Disk pressure | Size limits per tenant, cleanup policies |
| Multi-tenant data isolation | Data leakage | Row-level security via `tenant_id` filter (existing pattern) |
