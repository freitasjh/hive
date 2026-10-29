# Backend Unit Test Standards — Hive AI

## Framework & Setup

- **JUnit 5** + **Mockito** (via `mockito-inline` 5.11.0) + **AssertJ**.
- `@ExtendWith(MockitoExtension.class)` on test class.
- `@Mock` for dependencies, `@InjectMocks` for the class under test.

## Naming Convention

Pattern: `when_{action}_with_{scenario}_then_{result}()`

```java
@Test
void when_create_with_validAgent_then_success() { ... }

@Test
void when_update_with_nonExistingAgent_then_throwException() { ... }
```

## What to Test

### Service Layer (primary target)
- All `*ServiceImpl` classes must have unit tests.
- Happy path: create, update, delete, find operations.
- Error paths: entity not found, validation failures, business rule violations.
- Edge cases: null inputs, empty collections, concurrent modifications.

### Mappers (MapStruct)
- Test via `Mappers.getMapper(XxxMapper.class)` — do NOT mock MapStruct mappers.
- Verify field mapping correctness, especially `@Mapping(ignore = true)` fields.

### Domain Entities
- Business methods, invariants, validation logic.
- Static factory methods if present.

## Test Structure

```java
@ExtendWith(MockitoExtension.class)
class ExampleServiceImplTest {

    @Mock
    private SomeRepository repository;

    @Mock
    private SomeMapper mapper;

    @InjectMocks
    private ExampleServiceImpl service;

    @Test
    void when_action_with_scenario_then_result() {
        // Arrange
        // Act
        // Assert (AssertJ preferred)
        assertThat(result).isNotNull();
        verify(repository).save(any());
    }
}
```

## Rules

- **No Spring context** in unit tests — pure Mockito isolation.
- **No database** — repository calls are mocked.
- **No `@SpringBootTest`** — that's for integration tests.
- **AssertJ** preferred over JUnit assertions (`assertThat` > `assertEquals`).
- **Verify interactions** with `verify()` on mocks.
- **Test exceptions**: `assertThrows(ExceptionClass.class, () -> ...)`.
- **No sleep/wait** — use Mockito's `timeout()` if async testing needed.
