# Backend Integration Test Standards — Hive AI

> ✅ **STATUS (2026-09-24):** módulo `backend/integration-test/` **criado** (débito D1 pago — INFRA-IT-01). Base: `support/WebIntegrationTest.java` + `support/DatabaseCleaner.java` + `support/JwtTestHelper.java` + `src/test/resources/application-test.properties` (profile `test`). Todo controller novo exige `*IT.java` (ver `SmokeIT.java` como exemplo).


## Framework & Setup

- **JUnit 5** + **Spring Boot Test** + **MockMvc** (no RestAssured).
- **Testcontainers** 1.20.4 for PostgreSQL (with pgvector extension).
- Test classes suffixed with `*IT.java` (not `*Test.java`).
- Located in `backend/integration-test/src/test/java/` — nunca em módulos de domínio.

## What to Test

### REST Controllers
- All `*ControllerV1` classes must have integration tests.
- **Success paths**: 200, 201, 204 responses with correct body.
- **Error paths**: 400 (validation), 404 (not found), 409 (conflict).
- **Auth paths**: 401 (no token), 403 (insufficient permissions).
- **Request validation**: `@Valid` constraint violations return 400.

### Repository Layer
- JPA queries, `@Query` annotations, native queries.
- Pagination and sorting.
- Constraint violations (unique, not null, foreign key).

### Cross-Module Flows
- Authentication flow: login → JWT → access protected endpoint.
- Multi-tenant isolation: verify `tenantId` filter.

## Test Structure

```java
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ExampleControllerIT extends WebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void when_getAll_withAuthenticatedUser_then_returnsOk() throws Exception {
        mockMvc.perform(get(RestPath.V1 + "/resource")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
```

## Rules

- **PostgreSQL via Testcontainers** — no H2, no embedded database.
- **`@SpringBootTest`** with `RANDOM_PORT` for full context.
- **MockMvc** for HTTP layer testing (no RestAssured). No Spring Boot 4.1, `@AutoConfigureMockMvc` vive no artefato **`spring-boot-webmvc-test`** (pacote `org.springframework.boot.webmvc.test.autoconfigure`) — `spring-boot-test-autoconfigure` virou stub; declare a dependência `test` no `pom.xml` do módulo.
- **Clean database** between tests: `DatabaseCleaner.reset()` (`TRUNCATE ... CASCADE`, preserva `flyway_schema_history*`) em `@BeforeEach`. Proibido `repository.deleteAll()` para limpeza.
- **JWT tokens**: `JwtTestHelper` (`support/`) espelha `SecurityTokenServiceImpl` — sem segredo hardcoded, secret via `@Value` do profile `test`.
- **Profile**: use `@ActiveProfiles("test")` with test-specific properties.
- **Infra real via Testcontainers**: PostgreSQL `pgvector/pgvector:pg16` + Redis `redis:7-alpine` (login/refresh exigem Redis) via `@DynamicPropertySource` na base `WebIntegrationTest`.
- **Execução**: `mvn verify -pl integration-test -am` (requer Docker); failsafe roda `**/*IT.java`, surefire exclui `*IT`.
