# Database Migration Standards — Hive AI (Flyway)

## Module-Isolated Migrations

Each domain module has its **own Flyway configuration**, **own migration history table**, and **own migration location**. This allows independent schema evolution per bounded context.

| Module | Migration Location | History Table |
|--------|--------------------|---------------|
| identity | `classpath:/db/migration/identity_db` | `flyway_schema_history_identity` |
| tenant | `classpath:/db/migration/tenant_db` | `flyway_schema_history_tenant` |
| agent | `classpath:/db/migration/agent` | `flyway_schema_history_agent` |

## Configuration Pattern

Each module defines a `@Configuration` class in `module/dbupdate/src/main/java/`:

```java
@Configuration
public class AgentDbUpdateConfig {
    @Bean(initMethod = "migrate")
    public Flyway agentFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/agent")
                .table("flyway_schema_history_agent")
                .baselineOnMigrate(true)
                .load();
    }
}
```

## Naming Convention

- Pattern: `V{N}__description.sql`
- Versions start at V2 (V1 is baseline, pre-existing).
- Use double underscore `__` separator (Flyway convention).
- Description in snake_case: `V2__create_agent_tables.sql`, `V3__prompt_template_versioning.sql`

## SQL Conventions

- Use `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"` for UUID generation.
- Primary keys: `id UUID NOT NULL DEFAULT uuid_generate_v4()`.
- Timestamps: `create_at TIMESTAMP WITHOUT TIME ZONE`, `update_at TIMESTAMP WITHOUT TIME ZONE`.
- Multi-tenant tables include `tenant_id UUID`.
- Constraints named explicitly: `CONSTRAINT pk_agent PRIMARY KEY (id)`.
- Never use `ddl-auto=validate/create` — Flyway manages schema entirely.

## Adding a New Module Migration

1. Create `module/dbupdate/src/main/resources/db/migration/{module_name}/V{N}__description.sql`
2. Create `module/dbupdate/src/main/java/.../config/{Module}DbUpdateConfig.java`
3. Set unique history table name: `flyway_schema_history_{module}`
4. Add dbupdate module to parent `module/pom.xml`
5. Bootstrap `pom.xml` must depend on the new dbupdate module

## Rules

- **Never modify applied migrations** — create a new version instead.
- **Always use `IF NOT EXISTS`** for extensions.
- **Always name constraints** explicitly (PK, FK, unique).
- **One migration per logical change** — don't batch unrelated schema changes.
- **Test migrations** against PostgreSQL 16 (not H2 or embedded DB).
