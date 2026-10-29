# Backend Coding Standards — Hive AI

## Entity Conventions

- Entities extend `AbstractEntity` (multi-tenant) or `BaseEntity` (non-tenant).
- `AbstractEntity` provides: UUID id, createAt, updateAt, tenantId with Hibernate `@Filter`.
- **No Lombok.** All getters/setters/constructors written manually.
- Use `@Enumerated(EnumType.STRING)` for enum fields.
- Use `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns (PostgreSQL).
- Use `FetchType.LAZY` on `@ManyToOne`/`@OneToMany` by default.

## Repository Conventions

- Extend `JpaRepository<Entity, UUID>` directly.
- No custom base repository interface.
- Annotate with `@Repository`.

## Service Conventions

- Service interfaces in `module/api/` extend `CrudService<T>` for basic CRUD.
- Additional methods declared in the service interface.
- Implementations in `module/impl/` annotated with `@Service`.
- Use constructor injection (no `@Autowired` on fields).
- `@Transactional` on write operations (create/update/delete).
- Domain VOs (not entities) in service layer — mappers convert between them.

## Controller Conventions

- Controllers in `module/web/internal/v1/`.
- Map to `RestPath.V1 + "/resource"` (e.g., `@RequestMapping(RestPath.V1 + "/chat")`).
- Use `RestControllerTool.of().buildSuccessResponse()` for responses.
- Constructor injection for services and mappers.
- `@Valid` on request body parameters.
- Swagger annotations: `@Operation`, `@Tag`.

## Mapper Conventions

- **MapStruct** with `@Mapper(componentModel = "spring")` — used everywhere.
- Domain ↔ Entity mappers in `module/impl/` (e.g., `AgentMapperDomain`).
- DTO ↔ Domain mappers in `module/web/internal/v1/` (e.g., `ChatMapper`).
- Use `@Mapping(target = "...", ignore = true)` for auto-generated fields (createAt, updateAt, tenantId).

## Package Structure per Module

```
module/
├── api/          # Service interfaces, domain VOs, exceptions
│   └── src/main/java/.../{module}/api/
│       ├── domain/        # VOs (Agent, ChatRequest, etc.)
│       ├── exceptions/    # Domain exceptions
│       └── service/       # Service interfaces
├── impl/         # Implementation
│   └── src/main/java/.../{module}/impl/
│       ├── entity/        # JPA entities
│       ├── mapper/        # MapStruct domain mappers
│       ├── repository/    # JPA repositories
│       └── service/       # Service implementations
├── dbupdate/     # Flyway migrations
└── web/internal/v1/
    └── src/main/java/.../{module}/web/internal/v1/
        ├── controller/    # REST controllers
        ├── dto/           # Request/Response DTOs
        └── mapper/        # MapStruct DTO mappers
```

## Naming Conventions

- Entities: `{Name}Entity` (e.g., `AgentEntity`)
- Repositories: `{Name}Repository` (e.g., `AgentRepository`)
- Services: `{Name}Service` (interface), `{Name}ServiceImpl` (impl)
- Controllers: `{Name}ControllerV1` (e.g., `ChatControllerV1`)
- DTOs: `{Name}DTO` (e.g., `ChatRequestDTO`, `ChatResponseDTO`)
- Domain VOs: `{Name}` (e.g., `Agent`, `ChatRequest`)
- Exceptions: `{Name}NotFoundException`, `{Name}Exception`
