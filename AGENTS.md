# Hive AI — Agent Instructions

Multi-agent AI platform. Modular monolith backend + Vue 3 frontend.

## Quick Commands

```bash
# Backend (from backend/)
mvn clean install                          # full build
mvn spring-boot:run -pl bootstrap          # run app (port 8080)
mvn test                                   # unit tests all modules
mvn test -pl agent/impl                    # unit tests single module
mvn compile -pl agent/impl -am             # compile module + deps

# Frontend (from frontend/)
npm run dev                                # dev server (port 5173, proxies /hive → :8080)
npm run build                              # vue-tsc + vite build
npm run test                               # vitest run

# Infrastructure (from docker/)
docker-compose -f docker-compose-redis.yml up -d     # Redis on :6379
docker-compose -f docker-compose-vetor.yml up -d     # PostgreSQL+pgvector on :5432
```

## Architecture

**Backend**: Java 21, Spring Boot 4.1.0, PostgreSQL 16 + pgvector, Redis, RabbitMQ, LangChain4j.
**Frontend**: Vue 3.5, TypeScript 5.9, PrimeVue 4, Pinia, Vite 8, Vitest.

### Backend Module Structure

Each domain module (`tenant`, `identity`, `security`, `agent`, `provider`) follows:

```
module/
├── api/          # Service interfaces, domain VOs, exceptions (no framework deps)
├── impl/         # JPA entities, repositories, service impls, MapStruct mappers
├── dbupdate/     # Flyway migrations (independent history table per module)
└── web/internal/v1/  # REST controllers, DTO mappers
```

**Shared modules**: `commons/api` (CrudService), `commons/api-tools` (RestControllerTool, RestPath, ControllerExceptionHandler), `commons/impl` (BaseEntity, AbstractEntity with tenant filter).

**Bootstrap**: `bootstrap/` is the Spring Boot entry point. It depends on all modules.

### Key Patterns (verified in code)

- **Entities**: extend `AbstractEntity` → `BaseEntity` (UUID id, createAt, updateAt, tenantId with Hibernate `@Filter`)
- **Repositories**: extend `JpaRepository<Entity, UUID>` directly (no custom base repo)
- **Mappers**: MapStruct with `@Mapper(componentModel = "spring")` — used for both domain ↔ entity and DTO ↔ domain
- **Controllers**: constructor injection, use `RestControllerTool.of().buildSuccessResponse()` for responses
- **API versioning**: `RestPath.V1` = `"/v1"`, controllers mapped as `@RequestMapping(RestPath.V1 + "/resource")`
- **Context path**: all endpoints under `/hive` (configured in `application.properties`)
- **Auth**: `@HasPermission("PERMISSION_NAME")` on endpoints, JWT via Auth0 java-jwt

### Flyway Migrations

Each module has its **own migration history table** and **own location**:

| Module | Location | History Table |
|--------|----------|---------------|
| identity | `classpath:/db/migration/identity_db` | `flyway_schema_history_identity` |
| tenant | `classpath:/db/migration/tenant_db` | `flyway_schema_history_tenant` |
| agent | `classpath:/db/migration/agent` | `flyway_schema_history_agent` |

Naming: `V{N}__description.sql`. Start at V2 (V1 likely baseline). Use `uuid-ossp` extension for UUID generation.

### Frontend Structure

```
src/
├── views/        # Page components (Login, Register, Chat)
├── components/   # Reusable UI (TheTopbar, TheSidebar, TheFooterbar, ChatMessage, etc.)
├── stores/       # Pinia stores (auth.store.ts)
├── services/     # API layer (api.ts = axios instance, chat.service.ts)
├── router/       # Vue Router with auth guard
└── types/        # TypeScript types
```

- **API base**: axios instance at `src/services/api.ts`, baseURL `/hive/v1`, auto-injects Bearer token
- **Proxy**: Vite proxies `/hive` → `http://localhost:8080/hive`
- **Theme**: PrimeVue Aura preset with custom Deep Indigo + Amber palette (see `docs/design/DESIGN_SYSTEM.md`)
- **Dark mode**: via `.p-dark` CSS selector
- **Tests**: Vitest + vue-test-utils, files in `src/components/__tests__/*.spec.ts`

## Critical Rules

### What NOT to do

- **No Lombok** — getters/setters/constructors written manually
- **No Options API** in Vue — Composition API only (`<script setup lang="ts">`)
- **No `ddl-auto=validate/create`** — Flyway manages schema (`ddl-auto=none`)
- **No RestAssured** in integration tests — project uses MockMvc
- **No hardcoded secrets** — use env vars; dev defaults in `application-dev.properties`

### What TO do

- **MapStruct IS used** — `@Mapper(componentModel = "spring")` for all mappers (domain and DTO layers)
- **Test naming**: `when_{action}_with_{scenario}_then_{result}()`
- **Unit tests**: `@ExtendWith(MockitoExtension.class)`, `@Mock` + `@InjectMocks`, AssertJ
- **Entities**: manual getters/setters (no Lombok), extend `AbstractEntity` for multi-tenant domains
- **DTOs**: separate from domain VOs; DTO mappers in web layer, domain mappers in impl layer

### Dev Environment

- PostgreSQL requires `pgvector` extension (use `pgvector/pgvector:pg16` image)
- Redis on default port 6379, no password in dev
- JWT secret in dev: `api.security.token.secret=123456` (dev only!)
- Flyway disabled by default (`spring.flyway.enabled=false`) — modules run their own Flyway beans

## Reference Docs

| Path | Purpose |
|------|---------|
| `.agents/rules/*.md` | Workflow, security, SDD standards (auto-loaded by opencode) |
| `.agents/agents/*/AGENT.md` | Agent-specific instructions |
| `.agents/skills/*/SKILL.md` | Skill definitions |
| `.opencode/plugins/*.js` | Harness validator + HITL guardrail plugins |
| `docs/adr/*.md` | Architecture Decision Records |
| `docs/design/DESIGN_SYSTEM.md` | UI design tokens and component APIs |
| `backend/README.md` | Backend architecture overview |
