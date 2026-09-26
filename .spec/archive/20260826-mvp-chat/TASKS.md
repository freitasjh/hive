# TASKS — mvp-chat: Chat Persistente + Multi-Turn + Session CRUD

> Fonte: `SPEC.md` RF1-RF8, RN1-RN10, CA1-CA10, contratos §6, V5 §7.3, TX split §9, PLAN.md Batches 1-4.
> Convenções: TDD-ready, delegável isoladamente via `task` tool ao `developer-engineer`. Estimativa S (<2h), M (2-4h), L (>4h). Cada task inclui arquivo:linha alvo quando aplicável.
> Naming testes: `when_{action}_with_{scenario}_then_{result}()`.

---

## Batch 1 — Fundação Persistência (VOs + Entities + Repos + Mappers + V5)

> Gate Batch 1: `mvn compile -pl agent/impl,agent/dbupdate -am` verde; V5 aplica; unit mappers passam.

### BE-01 — VOs ChatSession + ChatMessage em agent/api

- **Título**: Criar VOs `ChatSession`, `ChatMessage` + `ChatMessageRole` + exceptions base
- **Descrição prescritiva**:
  - Criar `agent/api/src/main/java/com/hive/agent/api/domain/ChatSession.java` VO com campos `UUID id, UUID userId, UUID agentId, String title, String summary, Instant createAt, Instant updateAt, UUID tenantId` (SPEC RF1, Apêndice A). Manual getters/setters, construtor `ChatSession(UUID userId, UUID agentId, UUID tenantId, String title)`, sem Lombok, sem JPA.
  - Criar `agent/api/src/main/java/com/hive/agent/api/domain/ChatMessage.java` VO `UUID id, UUID sessionId, UUID tenantId, ChatMessageRole role, String content, Instant createAt`.
  - Criar `agent/api/src/main/java/com/hive/agent/api/domain/ChatMessageRole.java` enum `{USER, ASSISTANT}` (ou `user/assistant` lowercase via `@JsonProperty` se serializado).
  - Criar `agent/api/src/main/java/com/hive/agent/api/exceptions/ChatSessionNotFoundException.java` (`extends RuntimeException`, ctor `UUID id`), `ChatAgentNotFoundException.java` (UUID agentId). `ProviderException.java` se não existir em `provider` module (reusar existente se já houver — grep `ProviderException` antes).
  - Criar `agent/api/src/main/java/com/hive/agent/api/service/ChatSessionService.java` interface (métodos prescritos PLAN §4.1) e `ChatMessageService.java` se separado — assinaturas sem impl.
- **Critério de aceite**: `mvn compile -pl agent/api -am` passa; VOs sem dependência Spring/JPA; testes de compilação de import em `impl` resolvem.
- **CA vinculado**: CA1, CA5 (VOs base para persistência)
- **RN vinculado**: RN1 (title 50), RN2 (agentId campo)
- **Estimativa**: S
- **Dependências**: nenhuma
- **Subtasks**:
  - [ ] `ChatSession.java` criado com manual getters/setters
  - [ ] `ChatMessage.java` + `ChatMessageRole` criados
  - [ ] `ChatSessionNotFoundException` + `ChatAgentNotFoundException` criados
  - [ ] `ChatSessionService` interface criada com assinaturas `create`, `listSessions`, `getSession`, `getMessages`, `deleteSession`, `touchUpdateAt`, `findLast20`
  - [ ] `mvn compile -pl agent/api -am` verde

### BE-02 — Revisar ChatSessionEntity + ChatMessageEntity (V4 diverge)

- **Título**: Revisar/criar `ChatSessionEntity`/`ChatMessageEntity` estendendo `AbstractEntity`
- **Descrição prescritiva**:
  - Ler `agent/dbupdate/src/main/resources/db/migration/agent/V4__fix_agent_and_message_and_create_session.sql:14-26` (V4 criou `title VARCHAR(255)` nullable, `agent_id UUID` nullable) e `backend/commons/impl/src/main/java/com/hive/commons/impl/entity/AbstractEntity.java` (confirmar `@Filter(name="tenantFilter")`).
  - Criar/editar `agent/impl/src/main/java/com/hive/agent/impl/entity/ChatSessionEntity.java` estendendo `AbstractEntity` (UUID id, createAt, updateAt, tenantId + `@Filter`). Campos: `@Column(name="title", nullable=false, length=50) String title`, `@Column(name="summary", columnDefinition="TEXT") String summary`, `@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="agent_id", nullable=false) AgentEntity agent` OU `UUID agentId` com `@Column(name="agent_id", nullable=false)` — escolher conforme `AgentEntity` existente (grep `AgentEntity`); `@Column(name="user_id", nullable=false) UUID userId` (se `userId` não vem de `AbstractEntity`). Tabela `@Table(name="chat_session")`. Sem Lombok, manual getters/setters.
  - Criar/editar `agent/impl/src/main/java/com/hive/agent/impl/entity/ChatMessageEntity.java` estendendo `AbstractEntity`. Campos: `@ManyToOne(fetch=LAZY) @JoinColumn(name="session_id", nullable=false) ChatSessionEntity session` (N3.4), `@Enumerated(EnumType.STRING) @Column(name="role", nullable=false, length=20) ChatMessageRole role`, `@Column(name="content", nullable=false, columnDefinition="TEXT") String content`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name="metadata", columnDefinition="JSONB") Object metadata` se V4 `metadata JSONB` mantido (opcional, nullable). `@Table(name="chat_message")`. `FetchType.LAZY` obrigatório.
  - Validar `ddl-auto=none` em `application*.properties` (não alterar).
- **Critério de aceite**: `mvn compile -pl agent/impl -am` passa; entities mapeiam colunas V4+V5; `FetchType.LAZY` em `ChatMessageEntity.session`; `@Enumerated(STRING)` em `role`.
- **CA vinculado**: CA1, CA5
- **RN vinculado**: RN10 (tenant filter via AbstractEntity), N3.4 (LAZY)
- **Estimativa**: M
- **Dependências**: BE-01 (VOs para referência), V4 SQL lido
- **Subtasks**:
  - [ ] `ChatSessionEntity.java` criado/editado com `AbstractEntity`, `title` 50 NOT NULL, `agent` FK, `userId`
  - [ ] `ChatMessageEntity.java` criado/editado com `AbstractEntity`, `FetchType.LAZY`, `role` enum, `content` TEXT
  - [ ] Grep `AgentEntity` para decidir `agent` como entity vs UUID
  - [ ] `mvn compile -pl agent/impl -am` verde

### BE-03 — Repositories ChatSession + ChatMessage

- **Título**: Criar `ChatSessionRepository` + `ChatMessageRepository` com tenant filter
- **Descrição prescritiva**:
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/repository/ChatSessionRepository.java` com `@Repository extends JpaRepository<ChatSessionEntity, UUID>` + métodos prescritos PLAN §4.2:
    ```java
    Optional<ChatSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ChatSessionEntity> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);
    Page<ChatSessionEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId, Pageable pageable);
    void deleteByIdAndTenantId(UUID id, UUID tenantId);
    ```
    Nunca expor `findAll()` sem tenant (RN10). JPQL parametrizado, sem concatenação (A03).
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/repository/ChatMessageRepository.java` com `@Repository extends JpaRepository<ChatMessageEntity, UUID>` +:
    ```java
    Page<ChatMessageEntity> findBySessionIdAndTenantIdOrderByCreateAtAsc(UUID sessionId, UUID tenantId, Pageable pageable);
    // para window 20: usar @Query ou findTop20By...OrderByCreateAtDesc
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.id = :sessionId AND m.tenantId = :tenantId ORDER BY m.createAt DESC")
    List<ChatMessageEntity> findTop20BySessionIdAndTenantIdOrderByCreateAtDesc(@Param("sessionId") UUID sessionId, @Param("tenantId") UUID tenantId, Pageable pageable);
    long countBySessionIdAndTenantId(UUID sessionId, UUID tenantId);
    ```
    Alternativa: `List<ChatMessageEntity> findTop20BySessionTenantIdAndTenantIdOrderByCreateAtDesc` — validar naming JPA.
  - Verificar `ChatMessageEntity.session` field name (`session` vs `sessionId`) para query derivada.
- **Critério de aceite**: `mvn compile -pl agent/impl -am` passa; repos expõem apenas métodos com `tenantId`; nenhum `findAll` sem filtro; queries parametrizadas.
- **CA vinculado**: CA3, CA4, CA8 (isolamento)
- **RN vinculado**: RN10, RN8, RN3, N3.3, N3.2
- **Estimativa**: M
- **Dependências**: BE-02 (entities)
- **Subtasks**:
  - [ ] `ChatSessionRepository.java` com `findByIdAndTenantId`, `findByTenantIdAndUserId` paginado, `deleteByIdAndTenantId`
  - [ ] `ChatMessageRepository.java` com `findBySessionIdAndTenantIdOrderByCreateAtAsc` paginado + `findTop20...Desc` + `countBy...`
  - [ ] Grep `findAll()` inexistente nos novos repos
  - [ ] `mvn compile -pl agent/impl -am` verde

### BE-04 — Mappers domain MapStruct (ChatSession + ChatMessage)

- **Título**: Criar `ChatSessionMapperDomain` + `ChatMessageMapperDomain` MapStruct spring
- **Descrição prescritiva**:
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/mapper/ChatSessionMapperDomain.java` com `@Mapper(componentModel="spring")`:
    ```java
    @Mapping(target="createAt", ignore=true) @Mapping(target="updateAt", ignore=true) @Mapping(target="tenantId", ignore=true)
    ChatSessionEntity toEntity(ChatSession domain);
    ChatSession toDomain(ChatSessionEntity entity);
    // mapear agentId via entity.agent.id se ManyToOne, ou direto se UUID
    ```
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/mapper/ChatMessageMapperDomain.java` com `@Mapper(componentModel="spring")`:
    ```java
    @Mapping(target="createAt", ignore=true) @Mapping(target="updateAt", ignore=true) @Mapping(target="tenantId", ignore=true)
    @Mapping(target="session", ignore=true) // setado manualmente via session ref
    ChatMessageEntity toEntity(ChatMessage domain);
    @Mapping(target="sessionId", source="session.id")
    ChatMessage toDomain(ChatMessageEntity entity);
    ```
  - Registrar como `spring` component, sem `Mappers.getMapper` em produção (teste usa `Mappers.getMapper`).
- **Critério de aceite**: `mvn compile -pl agent/impl -am` passa; MapStruct gera `*Impl.java` em `target/generated-sources`; unit via `Mappers.getMapper()` verifica field mapping e `ignore` createAt/updateAt/tenantId.
- **CA vinculado**: CA1-CA5 (mapping correto)
- **RN vinculado**: — (infra mapping)
- **Estimativa**: S
- **Dependências**: BE-01 (VOs), BE-02 (entities)
- **Subtasks**:
  - [ ] `ChatSessionMapperDomain.java` `@Mapper(componentModel="spring")` com `ignore createAt/updateAt/tenantId`
  - [ ] `ChatMessageMapperDomain.java` com `session.id` → `sessionId` mapping
  - [ ] `mvn compile -pl agent/impl -am` gera `*Impl` sem erro
  - [ ] Unit `ChatSessionMapperDomainTest` + `ChatMessageMapperDomainTest` via `Mappers.getMapper()` passam

### BE-05 — Migration V5 idempotente

- **Título**: Criar `V5__fix_chat_session_message_constraints_and_indexes.sql` idempotente
- **Descrição prescritiva**:
  - Criar `agent/dbupdate/src/main/resources/db/migration/agent/V5__fix_chat_session_message_constraints_and_indexes.sql` com conteúdo prescrito PLAN §4.3 (adaptado ao V4 real). Incluir:
    - `CREATE EXTENSION IF NOT EXISTS "uuid-ossp";`
    - `UPDATE chat_session SET title=LEFT(title,50) WHERE LENGTH(title)>50;` + `UPDATE chat_session SET title='Nova conversa' WHERE title IS NULL;`
    - `ALTER TABLE chat_session ALTER COLUMN title TYPE VARCHAR(50) USING LEFT(title,50); ALTER COLUMN title SET NOT NULL;`
    - `ALTER TABLE chat_session ALTER COLUMN tenant_id SET NOT NULL; ALTER TABLE chat_message ALTER COLUMN tenant_id SET NOT NULL;`
    - `ALTER TABLE chat_message DROP CONSTRAINT IF EXISTS fk_chat_message_session; ADD CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE;`
    - `DO $$ IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_chat_session_agent') THEN ALTER TABLE chat_session ADD CONSTRAINT fk_chat_session_agent FOREIGN KEY (agent_id) REFERENCES agent(id); END IF; END $$;`
    - `CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);` + `idx_chat_message_tenant_session`, `idx_chat_session_tenant_user`, `idx_chat_session_agent`, `idx_chat_session_update_at`, `idx_chat_session_create_at`, `idx_chat_message_create_at`
    - `DO $$ IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_chat_message_role') THEN ALTER TABLE chat_message ADD CONSTRAINT chk_chat_message_role CHECK (role IN ('user','assistant')); END IF; END $$;`
  - Verificar `agent/dbupdate/src/main/java/.../config/AgentDbUpdateConfig.java` existe e usa `locations="classpath:/db/migration/agent"`, `table="flyway_schema_history_agent"`, `baselineOnMigrate=true` (ler arquivo antes de editar).
- **Critério de aceite**: `mvn flyway:migrate` (ou `spring-boot:run`) aplica V5 sem erro; re-execução idempotente (segundo migrate não falha); `SELECT * FROM flyway_schema_history_agent WHERE version='5' AND success=true` retorna 1 row; `pg_constraint` contém `fk_chat_message_session` com `confdeltype='c'`; `pg_indexes` contém índices.
- **CA vinculado**: CA5 (cascade), CA3/CA4 (índices paginação)
- **RN vinculado**: RN6, N3.5
- **Estimativa**: M
- **Dependências**: BE-02 (entities), V4 SQL lido
- **Subtasks**:
  - [ ] `V5__fix_chat_session_message_constraints_and_indexes.sql` criado idempotente (`IF NOT EXISTS`/`IF EXISTS`/`DO $$`)
  - [ ] `AgentDbUpdateConfig.java` verificado (locations/table/baselineOnMigrate)
  - [ ] `mvn compile -pl agent/dbupdate -am` verde
  - [ ] Flyway migrate aplica V5 + re-execução não falha (manual ou IT `FlywayV5IT`)

### BE-06 — Testes unit mappers domain (TDD Batch 1)

- **Título**: Unit tests `ChatSessionMapperDomain` + `ChatMessageMapperDomain`
- **Descrição prescritiva**:
  - Criar `agent/impl/src/test/java/com/hive/agent/impl/mapper/ChatSessionMapperDomainTest.java` com `@ExtendWith(MockitoExtension.class)`? Não — mapper sem mocks, usar `Mappers.getMapper(ChatSessionMapperDomain.class)` (não mockar MapStruct). Testes: `when_toDomain_with_entity_then_mapsAllFields()`, `when_toEntity_with_domain_then_ignoresCreateAtUpdateAtTenantId()`, `when_toDomain_with_agentEntity_then_mapsAgentId()`. AssertJ `assertThat`.
  - Criar `agent/impl/src/test/java/com/hive/agent/impl/mapper/ChatMessageMapperDomainTest.java` com `when_toDomain_with_entity_then_mapsSessionIdAndRole()`, `when_toEntity_with_domain_then_ignoresAutoFields()`, enum `USER`/`ASSISTANT` mapping.
  - Executar `mvn test -pl agent/impl -am -Dtest=Chat*MapperDomainTest`.
- **Critério de aceite**: `mvn test -pl agent/impl -Dtest=Chat*MapperDomainTest` verde; coverage mappers via teste direto; nunca mockar mappers.
- **CA vinculado**: CA1 (mapping base)
- **Estimativa**: S
- **Dependências**: BE-04 (mappers)
- **Subtasks**:
  - [ ] `ChatSessionMapperDomainTest.java` com `Mappers.getMapper()` + asserts `ignore` fields
  - [ ] `ChatMessageMapperDomainTest.java` com role enum + sessionId mapping
  - [ ] `mvn test -pl agent/impl -Dtest=Chat*MapperDomainTest` verde

### INFRA-01 — Config Flyway + verificação V5 (infra)

- **Título**: Verificar `AgentDbUpdateConfig` + `bootstrap/pom.xml` depende de `agent/dbupdate`
- **Descrição prescritiva**:
  - Ler `agent/dbupdate/src/main/java/com/hive/agent/dbupdate/config/AgentDbUpdateConfig.java` e `backend/bootstrap/pom.xml` (verificar `agent-dbupdate` dependency). Se `bootstrap` não depende de `agent/dbupdate`, adicionar dependency `com.hive:agent-dbupdate` no `bootstrap/pom.xml`.
  - Garantir `spring.flyway.enabled=false` global mas `AgentDbUpdateConfig` bean `initMethod="migrate"` ativo (padrão projeto). Verificar `application-dev.properties` `spring.flyway.enabled` ou `agent.flyway`.
  - Documentar em PLAN §4.3 que V5 usa `flyway_schema_history_agent` separado.
- **Critério de aceite**: `bootstrap/pom.xml` contém `agent-dbupdate`; `AgentDbUpdateConfig` com `locations=classpath:/db/migration/agent`, `table=flyway_schema_history_agent`, `baselineOnMigrate=true`; `mvn compile -pl bootstrap -am` verde.
- **CA vinculado**: — (infra)
- **Estimativa**: S
- **Dependências**: BE-05 (V5)
- **Subtasks**:
  - [ ] `AgentDbUpdateConfig.java` lido e validado
  - [ ] `bootstrap/pom.xml` verificado/adicionado dependency `agent-dbupdate` se faltar
  - [ ] `mvn compile -pl bootstrap -am` verde

---

## Batch 2 — Services Domínio (lógica RN1-RN10)

> Gate Batch 2: `mvn test -pl agent/impl` verde; services cobrem RN1-RN10, window 20, anti-IDOR, truncate, touch.

### BE-07 — ChatSessionServiceImpl (CRUD + paginação + anti-IDOR)

- **Título**: Implementar `ChatSessionServiceImpl` com CRUD paginado e isolamento tenant
- **Descrição prescritiva**:
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/service/ChatSessionServiceImpl.java` com `@Service`, constructor injection `ChatSessionRepository`, `ChatMessageRepository`, `AgentRepository`, `ChatSessionMapperDomain`, `ChatMessageMapperDomain`.
  - Métodos (SPEC RF4, RN1,RN5,RN6,RN8,RN10):
    - `ChatSession create(UUID agentId, String title, UUID tenantId, UUID userId)` → valida `agentRepository.findByIdAndTenantId(agentId, tenantId)` → 404 `ChatAgentNotFoundException` se ausente; `title = truncate(titleOrNull, 50)` (RN1: `truncate(trim(message),50)` sem "...", sem quebrar surrogate — usar `String.substring(0, Math.min(50, s.length()))` ou `codePointCount`); `new ChatSessionEntity(userId, agentId, tenantId, title)` → `repository.save(entity)` → `mapper.toDomain`; `@Transactional`.
    - `Page<ChatSession> listSessions(UUID tenantId, UUID userId, Pageable pageable)` → `repository.findByTenantIdAndUserId(tenantId, userId, PageRequest.of(page, size, Sort.by(DESC,"updateAt")))` (RN8); valida `size` default 20 max 50 (ou deixa para controller `Pageable`); map via `ChatSessionMapperDomain`.
    - `ChatSession getSession(UUID id, UUID tenantId, UUID userId)` → `repository.findByIdAndTenantIdAndUserId` ou `findByIdAndTenantId` + ownership check `entity.getUserId().equals(userId)` → 404 `ChatSessionNotFoundException` anti-IDOR (RN5).
    - `void deleteSession(UUID id, UUID tenantId, UUID userId)` → `getSession(id,tenantId,userId)` → `repository.deleteByIdAndTenantId(id, tenantId)` ou `repository.delete(entity)`; `@Transactional`; segundo delete → 404 idempotente (RN6).
    - `void touchUpdateAt(UUID sessionId, UUID tenantId)` → `findByIdAndTenantId` → `entity.setUpdateAt(Instant.now())` → save; `@Transactional` (chamado em TX2 do chat).
  - Toda query filtra `tenantId` (RN10, R1); sem `findAll()` sem tenant.
  - Usar `AbstractEntity` `tenantFilter` + `WHERE tenant_id=:tenantId` explícito.
- **Critério de aceite**: `mvn test -pl agent/impl -Dtest=ChatSessionServiceImplTest` verde; IT subsequente `GET /sessions` filtra tenant+user (CA3), `GET /sessions/{id}` anti-IDOR 404 (CA8), `DELETE` cascade (CA5).
- **CA vinculado**: CA1, CA3, CA5, CA8
- **RN vinculado**: RN1, RN5, RN6, RN8, RN10
- **Estimativa**: M
- **Dependências**: BE-01 (VOs/interface), BE-02 (entities), BE-03 (repos), BE-04 (mappers)
- **Subtasks**:
  - [ ] `ChatSessionServiceImpl.java` criado com `@Service`, constructor injection, `@Transactional` em writes
  - [ ] `create()` valida agent existe, `truncate(title,50)` RN1
  - [ ] `listSessions()` filtra `tenantId+userId`, `Sort updateAt DESC`, `Pageable`
  - [ ] `getSession()` anti-IDOR 404 (RN5)
  - [ ] `deleteSession()` `deleteByIdAndTenantId` cascade, idempotente 404
  - [ ] `touchUpdateAt()` atualiza `updateAt=now()`
  - [ ] Grep `findAll()` ausente no service

### BE-08 — ChatMessageServiceImpl (save + findLast20 + getMessages)

- **Título**: Implementar `ChatMessageServiceImpl` com window 20 e paginação ASC
- **Descrição prescritiva**:
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/service/ChatMessageServiceImpl.java` com `@Service`, constructor injection `ChatMessageRepository`, `ChatSessionRepository`, `ChatMessageMapperDomain`.
  - Métodos (SPEC RF2, RN3,RN8):
    - `ChatMessage save(UUID sessionId, UUID tenantId, ChatMessageRole role, String content)` → valida `sessionRepository.findByIdAndTenantId(sessionId, tenantId)` → 404 se não existe (anti-IDOR); valida `content` 1..4000 (ou deixa para DTO `@Valid`, mas service re-valida); `new ChatMessageEntity(session, tenantId, role, content)` → `repository.save` → `mapper.toDomain`; `@Transactional`.
    - `Page<ChatMessage> getMessages(UUID sessionId, UUID tenantId, Pageable pageable)` → valida session ownership (`findByIdAndTenantId` → 404); `repository.findBySessionIdAndTenantIdOrderByCreateAtAsc(sessionId, tenantId, PageRequest.of(page, size, Sort.by(ASC,"createAt")))` (RN8, N3.2); `size` default 50 max 100; map via mapper.
    - `List<ChatMessage> findLast20(UUID sessionId, UUID tenantId)` → `repository.findTop20BySessionIdAndTenantIdOrderByCreateAtDesc(sessionId, tenantId, PageRequest.of(0,20,Sort.by(DESC,"createAt")))` + `Collections.reverse()` para ASC (RN3, N3.3); retorna cronológica ASC, max 20.
  - `FetchType.LAZY` já em entity; service evita N+1 via single query (R2).
- **Critério de aceite**: `mvn test -pl agent/impl -Dtest=ChatMessageServiceImplTest` verde; `when_findLast20_with_25Messages_then_returnsOnlyLast20OrderedAsc()` passa; `when_getMessages_with_pagination_then_returnsAscWithCorrectPage()` passa.
- **CA vinculado**: CA2, CA4
- **RN vinculado**: RN3, RN8, RN5, R2
- **Estimativa**: M
- **Dependências**: BE-02, BE-03, BE-04
- **Subtasks**:
  - [ ] `ChatMessageServiceImpl.java` criado com `save()`, `getMessages()`, `findLast20()`
  - [ ] `save()` valida session anti-IDOR 404
  - [ ] `getMessages()` `Sort createAt ASC`, `Pageable` default 50 max 100
  - [ ] `findLast20()` `LIMIT 20` DESC + reverse ASC, `verify(repo, times(1))`
  - [ ] Unit `ChatMessageServiceImplTest` com window 20 e paginação

### BE-09 — Testes unit services (ChatSession + ChatMessage)

- **Título**: Unit tests `ChatSessionServiceImplTest` + `ChatMessageServiceImplTest` TDD
- **Descrição prescritiva**:
  - Criar `agent/impl/src/test/java/com/hive/agent/impl/service/ChatSessionServiceImplTest.java` com `@ExtendWith(MockitoExtension.class)`, `@Mock ChatSessionRepository`, `@Mock ChatMessageRepository`, `@Mock AgentRepository`, `@Mock ChatSessionMapperDomain`, `@InjectMocks ChatSessionServiceImpl`.
  - Casos (naming `when_then`):
    - `when_create_with_validAgent_then_successAndTitleTruncated50()` (CA1, RN1)
    - `when_create_with_nonExistingAgent_then_throwAgentNotFound()` (CA9)
    - `when_listSessions_with_tenantAndUser_then_filtersAndSortsByUpdateAtDesc()` (CA3)
    - `when_getSession_with_otherTenant_then_throwNotFound()` (CA8, RN5)
    - `when_delete_with_existingSession_then_deletesAndCascade()` (CA5, RN6)
    - `when_delete_with_nonExistingSession_then_throwNotFoundIdempotent()` (CA5)
    - `when_create_with_titleLongerThan50_then_truncatesWithoutEllipsis()` (RN1)
  - Criar `agent/impl/src/test/java/com/hive/agent/impl/service/ChatMessageServiceImplTest.java` com `@Mock ChatMessageRepository`, `@Mock ChatSessionRepository`, `@InjectMocks ChatMessageServiceImpl`.
    - `when_save_with_validSession_then_persistsWithTenantId()` (RF1)
    - `when_save_with_nonExistingSession_then_throwNotFound()` (RN5)
    - `when_findLast20_with_25Messages_then_returnsLast20Asc()` (RN3)
    - `when_getMessages_with_pagination_then_returnsAsc()` (CA4, RN8)
    - `when_save_with_contentExceeds4000_then_throwValidation()` (se validado em service)
  - AssertJ `assertThat`, `verify(repo).save(any())`, `ArgumentCaptor` para title truncate.
- **Critério de aceite**: `mvn test -pl agent/impl -Dtest=Chat*ServiceImplTest` verde; 80% service coverage; testes isolados sem DB.
- **CA vinculado**: CA1, CA3-CA5, CA8, CA9
- **RN vinculado**: RN1, RN3, RN5, RN6, RN8, R1
- **Estimativa**: M
- **Dependências**: BE-07, BE-08
- **Subtasks**:
  - [ ] `ChatSessionServiceImplTest.java` com 7+ casos `when_then`
  - [ ] `ChatMessageServiceImplTest.java` com window 20 + paginação
  - [ ] `mvn test -pl agent/impl -Dtest=Chat*ServiceImplTest` verde
  - [ ] `verify()` e `ArgumentCaptor` para truncate e tenant filter

### BE-10 — ChatServiceImpl refator TX split (coração multi-turn)

- **Título**: Refatorar `ChatServiceImpl.chat()` com TX split RN4 + window 20 + RN2
- **Descrição prescritiva**:
  - Ler `agent/impl/src/main/java/com/hive/agent/impl/service/ChatServiceImpl.java` existente (stateless atual SPEC §2.1). Grep `Provider`, `AgentRepository`, `ChatService` para wiring LLM.
  - Refator `ChatServiceImpl` (ou criar `ChatOrchestratorService` se preferir separar orquestração de provider — decisão: refatorar existente para não duplicar). Assinatura: `ChatResponseDTO chat(ChatRequestDTO req, UUID tenantId, UUID userId)` OU `ChatResponse chat(UUID agentId, UUID sessionId, String message, UUID tenantId, UUID userId)` — alinhar com controller.
  - Lógica prescrita SPEC RF2, FU1-FU2, RN1-RN4, RN9:
    1. Validar `req.message` 1..4000 (ou @Valid já), `req.agentId` not null.
    2. Se `req.sessionId == null`: `session = chatSessionService.create(req.agentId, truncate(req.message,50), tenantId, userId)` (RN1) → `sessionId = session.getId()`.
    3. Se `req.sessionId != null`: `session = chatSessionService.getSession(req.sessionId, tenantId, userId)` → 404 anti-IDOR (RN5); **RN2**: se `req.agentId != session.getAgentId()` **ignorar** `req.agentId`, usar `session.getAgentId()` para LLM (não 400 no MVP; documentar).
    4. **TX1**: `chatMessageService.save(sessionId, tenantId, USER, req.message)` com `@Transactional` em método privado `saveUserMessage()` (ou service call já transacional). Commit.
    5. **Fora de TX**: `history = chatMessageService.findLast20(sessionId, tenantId)` (RN3, window 20 ASC) + montar prompt (history + newMessage) → `provider.chat(history, session.getAgentId())` ou `agentLlmService.chat(history)` — pode lançar `ProviderException`.
    6. **TX2**: `chatMessageService.save(sessionId, tenantId, ASSISTANT, assistantText)` + `chatSessionService.touchUpdateAt(sessionId, tenantId)` com `@Transactional` em `saveAssistantMessageAndTouch()` (ou dois calls transacionais). Commit.
    7. Retorna `new ChatResponseDTO(sessionId, "assistant", assistantText, Instant.now())`.
    8. **Falha provider** (RN9, R7): catch `ProviderException` → não executa TX2, lança para controller mapear 502, **preserva** userMsg (TX1 já commitado). Método orquestrador **sem** `@Transactional` (crucial R4).
  - Injeção: `ChatSessionService`, `ChatMessageService`, `ProviderService`/`LlmProvider`, `ChatSessionRepository` se necessário para validar agent.
  - Logar `sessionId, userId, tenantId, agentId` sem `content` (N5.1).
- **Critério de aceite**: `mvn test -pl agent/impl -Dtest=ChatServiceImplTest` verde; casos: `when_chat_with_noSessionId_then_createsSessionAndSavesTwoMessages` (CA1), `when_chat_with_sessionId_then_appendsAndSendsHistory` (CA2), `when_chat_with_divergentAgentId_then_ignoresNewAgent` (RN2/CA7), `when_chat_with_providerFailure_then_preservesUserMsgAndThrows502` (RN4/RN9), `when_chat_with_historyMoreThan20_then_sendsOnlyLast20` (RN3), `when_chat_with_nonExistingSessionId_then_throw404` (CA8).
- **CA vinculado**: CA1, CA2, CA7, CA8
- **RN vinculado**: RN1, RN2, RN3, RN4, RN9, R4, R7
- **Estimativa**: L
- **Dependências**: BE-07, BE-08, BE-03 (Provider wiring lido)
- **Subtasks**:
  - [ ] `ChatServiceImpl.java` lido + provider wiring identificado (grep `Provider`)
  - [ ] `chat()` refatorado sem `@Transactional` no orquestrador, `saveUserMessage()` TX1 + `saveAssistantMessageAndTouch()` TX2
  - [ ] Lógica `sessionId==null` cria session `truncate(message,50)` RN1
  - [ ] Lógica `sessionId!=null` anti-IDOR RN5 + RN2 ignora divergente agentId
  - [ ] `findLast20` window 20 RN3 + `provider.chat(history)` fora de TX RN4
  - [ ] `touchUpdateAt` em TX2 + retorno `ChatResponseDTO`
  - [ ] `ProviderException` preserva TX1, mapeia 502 RN9
  - [ ] `mvn test -pl agent/impl -Dtest=ChatServiceImplTest` verde com 6+ casos `when_then`

### INFRA-02 — Exceptions + truncate util (infra)

- **Título**: Criar exceptions + util `truncate(title,50)` RN1
- **Descrição prescritiva**:
  - Criar `agent/api/src/main/java/com/hive/agent/api/exceptions/ChatSessionNotFoundException.java` se não em BE-01: `extends RuntimeException`, `public ChatSessionNotFoundException(UUID id){ super("ChatSession not found: "+id); }` + `UUID sessionId` field.
  - Criar `agent/api/src/main/java/com/hive/agent/api/exceptions/ProviderException.java` se não existir (ou `ChatProviderException`): ctor `String message, Throwable cause`.
  - Criar `agent/impl/src/main/java/com/hive/agent/impl/util/ChatTitleUtil.java` (ou método privado em `ChatSessionServiceImpl`): `public static String truncate(String s, int max){ if(s==null) return "Nova conversa"; s=s.trim(); if(s.length()<=max) return s; return s.substring(0,max); }` — sem "...", sem quebrar surrogate simples (MVP usa substring; se `codePointCount` > max, opcional `s.codePoints().limit(max).collect(...)`).
  - Garantir `ControllerExceptionHandler` mapeia `ChatSessionNotFoundException` → 404 `problem+json` (verificar `commons/api-tools` `ControllerExceptionHandler.java` — grep `ExceptionHandler`).
- **Critério de aceite**: Exceptions criadas; `ChatTitleUtil.truncate("x".repeat(51),50).length()==50`; `ChatTitleUtil.truncate(null,50)=="Nova conversa"`; `ControllerExceptionHandler` cobre 404.
- **CA vinculado**: CA9 (validação 400/404)
- **RN vinculado**: RN1, RN5, RN7
- **Estimativa**: S
- **Dependências**: BE-01
- **Subtasks**:
  - [ ] `ChatSessionNotFoundException.java` + `ProviderException.java` criados
  - [ ] `ChatTitleUtil.java` com `truncate(title,50)` sem "..."
  - [ ] `ControllerExceptionHandler` verificado para 404 `ChatSessionNotFoundException`
  - [ ] Unit `ChatTitleUtilTest` (opcional S) `when_truncate_with_51Chars_then_50WithoutEllipsis`

---

## Batch 3 — API + Controllers (DTOs + Mappers + Chat multi-turn API)

> Gate Batch 3: `mvn verify -pl integration-test -Dit.test=ChatControllerIT,ChatSessionControllerIT` verde; `fullstack-code-reviewer` zero 🔴.

### BE-11 — DTOs + validação Bean Validation

- **Título**: Criar DTOs `ChatRequestDTO`, `ChatResponseDTO`, `CreateSessionDTO`, `ChatSessionDTO`, `ChatMessageDTO` (+ `UpdateSessionDTO` EXTRA) com `@Valid`
- **Descrição prescritiva**:
  - Criar em `agent/web/internal/v1/src/main/java/com/hive/agent/web/internal/v1/dto/` (verificar path real via `ls agent/web/internal/v1/src/main/java` — pode ser `com/hive/...`):
    - `ChatRequestDTO.java` `record ChatRequestDTO(@NotNull UUID agentId, UUID sessionId, @NotBlank @Size(max=4000) String message)` (SPEC §6.2).
    - `ChatResponseDTO.java` `record ChatResponseDTO(UUID sessionId, String role, String message, Instant timestamp)`.
    - `CreateSessionDTO.java` `record CreateSessionDTO(@NotNull UUID agentId, @Size(max=50) String title)` (title nullable).
    - `UpdateSessionDTO.java` `record UpdateSessionDTO(@NotBlank @Size(max=50) String title)` EXTRA PATCH.
    - `ChatSessionDTO.java` `record ChatSessionDTO(UUID id, UUID userId, UUID agentId, String title, String summary, Instant createAt, Instant updateAt)`.
    - `ChatMessageDTO.java` `record ChatMessageDTO(UUID id, UUID sessionId, String role, String content, Instant createAt)`.
  - Todos com `jakarta.validation.constraints.*`, `@Valid` no controller param. `role` String `user|assistant`.
  - Verificar `RestPath.V1 = "/v1"` em `commons/api-tools/src/main/java/.../RestPath.java`.
- **Critério de aceite**: `mvn compile -pl agent/web -am` passa; DTOs com Bean Validation; `ChatRequestDTO` com `@NotBlank @Size(max=4000)` em `message`; `CreateSessionDTO.title` max 50; `UpdateSessionDTO.title` not blank max 50.
- **CA vinculado**: CA9 (400 RFC7807), CA1-CA5
- **RN vinculado**: RN7, RN1
- **Estimativa**: S
- **Dependências**: BE-01 (VOs para referência), BE-07 (services)
- **Subtasks**:
  - [ ] `ChatRequestDTO.java` com `@NotNull agentId`, `@NotBlank @Size(max=4000) message`, nullable `sessionId`
  - [ ] `ChatResponseDTO.java` com `sessionId`, `role=assistant`, `message`, `timestamp`
  - [ ] `CreateSessionDTO.java` `@NotNull agentId`, `@Size(max=50) title` nullable
  - [ ] `ChatSessionDTO.java` + `ChatMessageDTO.java` response DTOs
  - [ ] `UpdateSessionDTO.java` EXTRA `@NotBlank @Size(max=50) title`
  - [ ] `mvn compile -pl agent/web -am` verde

### BE-12 — Mappers DTO MapStruct

- **Título**: Criar `ChatSessionMapperDTO` + `ChatMessageMapperDTO` MapStruct spring
- **Descrição prescritiva**:
  - Criar `agent/web/internal/v1/src/main/java/com/hive/agent/web/internal/v1/mapper/ChatSessionMapperDTO.java` com `@Mapper(componentModel="spring")`:
    ```java
    ChatSessionDTO toDto(ChatSession domain);
    ChatSession toDomain(CreateSessionDTO dto); // userId/tenantId ignorados, setados em service
    Page<ChatSessionDTO> toDtoPage(Page<ChatSession> page); // ou manual map
    ```
  - Criar `agent/web/internal/v1/src/main/java/com/hive/agent/web/internal/v1/mapper/ChatMessageMapperDTO.java` com `@Mapper(componentModel="spring")`:
    ```java
    ChatMessageDTO toDto(ChatMessage domain);
    // role enum -> String
    ```
  - `role` `ChatMessageRole.USER` → `"user"`, `ASSISTANT` → `"assistant"` (MapStruct `expression` ou `default`).
- **Critério de aceite**: `mvn compile -pl agent/web -am` gera `*Impl` MapStruct; DTO mapping cobre todos os campos response; `role` enum→String.
- **CA vinculado**: CA3, CA4 (Page<DTO> projection)
- **RN vinculado**: N1.4 (DTO projection, nunca serializar entity)
- **Estimativa**: S
- **Dependências**: BE-11 (DTOs), BE-01 (VOs)
- **Subtasks**:
  - [ ] `ChatSessionMapperDTO.java` `@Mapper(componentModel="spring")` + `toDto` + `toDomain`
  - [ ] `ChatMessageMapperDTO.java` com role enum→String
  - [ ] `mvn compile -pl agent/web -am` gera impls
  - [ ] Unit mappers via `Mappers.getMapper()` verificam projection

### BE-13 — Controllers Chat + Session (com @HasPermission, @Valid, Pageable)

- **Título**: Criar `ChatControllerV1` + `ChatSessionControllerV1` com CRUD, paginação, anti-IDOR
- **Descrição prescritiva**:
  - Criar `agent/web/internal/v1/src/main/java/com/hive/agent/web/internal/v1/controller/ChatControllerV1.java`:
    ```java
    @RestController @RequestMapping(RestPath.V1 + "/chat") @Tag(name="chat")
    public class ChatControllerV1 {
      private final ChatService chatService; // ou ChatServiceImpl
      private final ChatSessionService chatSessionService;
      @PostMapping
      @HasPermission("CHAT_SEND")
      public ResponseEntity<?> chat(@Valid @RequestBody ChatRequestDTO req, Authentication auth) {
        UUID tenantId = TenantContext.getTenantId(); // ou SecurityContextHolder
        UUID userId = extractUserId(auth); // JWT sub
        ChatResponseDTO resp = chatService.chat(req, tenantId, userId);
        return RestControllerTool.of().buildSuccessResponse(resp); // 200
      }
    }
    ```
  - Criar `agent/web/internal/v1/src/main/java/com/hive/agent/web/internal/v1/controller/ChatSessionControllerV1.java`:
    ```java
    @RestController @RequestMapping(RestPath.V1 + "/chat/sessions") @Tag(name="chat-sessions")
    public class ChatSessionControllerV1 {
      @PostMapping @HasPermission("CHAT_SESSION_MANAGE") ResponseEntity<?> create(@Valid @RequestBody CreateSessionDTO dto) -> 201 ChatSessionDTO
      @GetMapping @HasPermission("CHAT_SESSION_MANAGE") ResponseEntity<?> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, Pageable pageable) -> 200 Page<ChatSessionDTO> (Sort updateAt DESC, size max 50 -> 400 se >50)
      @GetMapping("/{id}") @HasPermission("CHAT_SESSION_MANAGE") ResponseEntity<?> get(@PathVariable UUID id) -> 200/404 anti-IDOR
      @GetMapping("/{id}/messages") @HasPermission("CHAT_SESSION_MANAGE") ResponseEntity<?> messages(@PathVariable UUID id, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) -> 200 Page<ChatMessageDTO> ASC, size max 100
      @DeleteMapping("/{id}") @HasPermission("CHAT_SESSION_MANAGE") ResponseEntity<Void> delete(@PathVariable UUID id) -> 204/404
      @PatchMapping("/{id}") @HasPermission("CHAT_SESSION_MANAGE") EXTRA ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody UpdateSessionDTO dto) -> 200/400/404
    }
    ```
  - **Auth extraction**: verificar padrão existente `identity/web` ou `security` module — grep `TenantContext`, `SecurityContextHolder`, `JwtAuthenticationToken` em `agent/web`. Usar mesmo padrão; `tenantId` via `TenantContext.getTenantId()` ou `HttpHeader X-Tenant-ID` ou JWT claim `tenant_id`.
  - **Paginação**: `PageRequest.of(page, size, Sort.by(DESC,"updateAt"))` para sessions, `Sort.by(ASC,"createAt")` para messages; validar `size` > max → 400 via `@RequestParam` ou service `if(size>max) throw BadRequest`.
  - **Responses**: `RestControllerTool.of().buildSuccessResponse(dto)` para single, `buildPaginatedResponse(page)` ou `buildSuccessResponse(page)` conforme projeto (grep `RestControllerTool`).
  - **Permissões**: `CHAT_SEND` para `POST /v1/chat`, `CHAT_SESSION_MANAGE` para CRUD sessions (SPEC §6). Constante centralizada `Permissions.CHAT_SEND` se existir; senão string literal.
  - **Exception mapping**: `ChatSessionNotFoundException` → 404, `ChatAgentNotFoundException` → 404, `ProviderException` → 502, `MethodArgumentNotValidException` → 400 `application/problem+json` via `ControllerExceptionHandler` existente.
- **Critério de aceite**: `mvn compile -pl agent/web -am` passa; controllers anotados `@HasPermission`, `@Valid`, `RestPath.V1 + "/chat"`; `GET /sessions` Pageable, `GET /{id}/messages` Pageable ASC, `DELETE` 204, anti-IDOR 404 (não 403).
- **CA vinculado**: CA1-CA5, CA8, CA9
- **RN vinculado**: RN5, RN6, RN7, RN8, R1, R6
- **Estimativa**: M
- **Dependências**: BE-10 (chat service TX split), BE-11 (DTOs), BE-12 (DTO mappers), BE-07 (session service)
- **Subtasks**:
  - [ ] `ChatControllerV1.java` `@RequestMapping(RestPath.V1 + "/chat")` + `@HasPermission("CHAT_SEND")` + `@Valid ChatRequestDTO` → 200 `ChatResponseDTO`
  - [ ] `ChatSessionControllerV1.java` `@RequestMapping(RestPath.V1 + "/chat/sessions")` + CRUD `CHAT_SESSION_MANAGE` + `Pageable` + `Sort updateAt DESC` / `createAt ASC`
  - [ ] `POST /sessions` → 201 `ChatSessionDTO`
  - [ ] `GET /sessions` paginado `tenantId+userId` → 200 `Page<ChatSessionDTO>`
  - [ ] `GET /{id}` + `GET /{id}/messages` + `DELETE /{id}` + `PATCH /{id}` EXTRA com 404 anti-IDOR, 204, 400
  - [ ] `tenantId`/`userId` extraídos de `SecurityContext`/`TenantContext` (grep padrão existente)
  - [ ] `RestControllerTool` responses + `ControllerExceptionHandler` cobre 404/502/400
  - [ ] Grep `@HasPermission` presente em todos os endpoints ` /v1/chat/**`
  - [ ] `mvn compile -pl agent/web -am` verde

### BE-14 — Integration tests Chat*IT (MockMvc + Testcontainers)

- **Título**: ITs `ChatControllerIT` + `ChatSessionControllerIT` + `FlywayV5IT` (MockMvc, PostgreSQL)
- **Descrição prescritiva**:
  - Criar `backend/integration-test/src/test/java/com/hive/agent/ChatControllerIT.java` com `@SpringBootTest(webEnvironment=RANDOM_PORT) @AutoConfigureMockMvc @ActiveProfiles("test")` estendendo `WebIntegrationTest` (SAAS) (verificar `integration-test/src/test/java/com/hive/.../WebIntegrationTest.java` existente). Usar `MockMvc`, `JwtTestHelper.loginAndGetToken()` ou `TestUserFactory`/`TestTenantFactory`, `DatabaseCleaner.reset()` com `TRUNCATE ... CASCADE`.
  - Mock `Provider`/`LlmProvider` via `@MockBean` para controlar `assistantText` e falha (`ProviderException` → 502).
  - Casos `ChatControllerIT` (SPEC CA1,CA2,CA8,CA9, RN4,RN9):
    - `when_postChat_with_noSessionId_then_200AndCreatesSessionWithTwoMessages()` (CA1)
    - `when_postChat_with_sessionId_then_200AndAppendsAndHistoryContainsPreviousTurn()` (CA2, provider captor history contém "Meu nome é João")
    - `when_postChat_with_sessionIdOfOtherTenant_then_404()` (CA8, RN5)
    - `when_postChat_withoutJwt_then_401()` (CA8)
    - `when_postChat_withoutPermission_then_403()` (CA8)
    - `when_postChat_with_invalidBody_then_400ProblemJson()` (CA9, `agentId=null`, `message=""`, `message 4001 chars`)
    - `when_postChat_with_nonExistingAgent_then_404()` (CA9)
    - `when_postChat_with_providerFailure_then_502AndUserMsgPreserved()` (RN9, RN4 — verificar DB `countBySessionId`=1 userMsg)
  - Criar `backend/integration-test/src/test/java/com/hive/agent/ChatSessionControllerIT.java`:
    - `when_postSession_then_201()` (CA1)
    - `when_getSessions_with_twoSessionsForU1AndOneForU2_then_filtersTenantUserAndSortsDesc()` (CA3, criar 2 sessões U1/T1, 1 U2/T1, 1 T2)
    - `when_getMessages_with_6Messages_then_returnsAscAndPagination()` (CA4, `page=0&size=2` → 2 primeiras, `page=1` → próximas 2)
    - `when_deleteSession_then_204AndCascadeDeletesMessagesAndSecondDelete404()` (CA5, `SELECT COUNT(*) FROM chat_message WHERE session_id=?` =0)
    - `when_getSession_with_otherTenant_then_404AntiIdor()` (CA8)
    - `when_getSessions_withoutJwt_then_401()`, `withoutPermission then 403` (CA8)
    - `when_postSession_with_title51_then_400()` (CA9)
    - `when_patchSession_with_validTitle_then_200()` EXTRA se PATCH implementado
  - Criar `FlywayV5IT.java` ou adicionar em `ChatSessionControllerIT` setup: `SELECT version FROM flyway_schema_history_agent WHERE version='5'` success, `pg_constraint` FK cascade, `pg_indexes` índices, `information_schema.columns` `title VARCHAR(50) NOT NULL`.
  - Localização: `backend/integration-test/src/test/java/` (não em `agent/impl`). Arquivos `*IT.java` (não `*Test.java`).
- **Critério de aceite**: `mvn verify -pl integration-test -Dit.test=ChatControllerIT,ChatSessionControllerIT` verde; Testcontainers PostgreSQL `pgvector/pgvector:pg16`; `DatabaseCleaner.reset()` sem `repository.deleteAll()`; MockMvc (sem RestAssured); paginação, anti-IDOR 404, 401/403/400/502 cobertos; `EXPLAIN` não necessário mas índice usado.
- **CA vinculado**: CA1-CA5, CA8, CA9 (todos)
- **RN vinculado**: RN1-RN10, N3.1-N3.5, R1,R3,R4,R6,R7
- **Estimativa**: L
- **Dependências**: BE-13 (controllers), BE-05 (V5), BE-10 (TX split)
- **Subtasks**:
  - [ ] `ChatControllerIT.java` com 8+ casos (CA1,CA2,CA8,CA9, provider 502)
  - [ ] `ChatSessionControllerIT.java` com 8+ casos (CA1,CA3,CA4,CA5,CA8,CA9, PATCH EXTRA)
  - [ ] `FlywayV5IT` verifica V5, FK cascade, índices, `title 50`
  - [ ] `@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureMockMvc` + Testcontainers `pgvector/pgvector:pg16`
  - [ ] `DatabaseCleaner.reset()` + `JwtTestHelper.loginAndGetToken()` + `MockBean Provider`
  - [ ] Sem `H2`, sem `@WebMvcTest`, sem `deleteAll()`, sem `RestAssured`
  - [ ] `mvn verify -pl integration-test -Dit.test=ChatControllerIT,ChatSessionControllerIT` verde

---

## Batch 4 — Frontend Real (services + stores + components)

> Gate Batch 4: `npm run test:run` + `npm run build` verde; Chrome DevTools Network+Console zero erros.

### FE-01 — Services `session.service.ts` + `chat.service.ts` refator + `agent.service.ts`

- **Título**: Criar/refatorar services axios `session`, `chat`, `agent`
- **Descrição prescritiva**:
  - Criar `frontend/src/services/session.service.ts` com `import api from './api'` (`baseURL /hive/v1`, interceptor `Authorization: Bearer {hive_token}` existente em `frontend/src/services/api.ts` — ler antes). Métodos:
    ```ts
    export const sessionService = {
      listSessions(page=0, size=20): Promise<Page<ChatSessionDTO>> => api.get('/chat/sessions', {params:{page,size}}).then(r=>r.data.data),
      createSession(agentId:string, title?:string): Promise<ChatSessionDTO> => api.post('/chat/sessions', {agentId,title}).then(r=>r.data.data),
      getSession(id:string): Promise<ChatSessionDTO> => api.get(`/chat/sessions/${id}`).then(r=>r.data.data),
      getMessages(sessionId:string, page=0, size=50): Promise<Page<ChatMessageDTO>> => api.get(`/chat/sessions/${sessionId}/messages`, {params:{page,size}}).then(r=>r.data.data),
      deleteSession(id:string): Promise<void> => api.delete(`/chat/sessions/${id}`),
      updateSession(id:string, title:string): Promise<ChatSessionDTO> => api.patch(`/chat/sessions/${id}`, {title}).then(r=>r.data.data), // EXTRA
    }
    ```
  - Refatorar `frontend/src/services/chat.service.ts` existente (ler `frontend/src/services/chat.service.ts` — atualmente mock/hard-coded SPEC §2.1): método `sendMessage(agentId:string, sessionId:string|null, message:string): Promise<ChatResponseDTO>` via `api.post('/chat', {agentId, sessionId, message})`.
  - Criar/estender `frontend/src/services/agent.service.ts` (ler se existe): `listAgents(page,size): Promise<Page<Agent>>` via `GET /v1/agents` (SPEC D5). Se já existir, apenas verificar.
  - Tipos `frontend/src/types/` `ChatSessionDTO`, `ChatMessageDTO`, `ChatResponseDTO` se não existirem.
- **Critério de aceite**: `npm run build` ok; services usam `api.ts` `baseURL /hive/v1`; sem hardcoded URL; `Authorization` header auto; métodos cobertos por `src/services/__tests__/session.service.spec.ts` com `axios-mock-adapter`.
- **CA vinculado**: CA3, CA4, CA5
- **RN vinculado**: — (infra FE)
- **Estimativa**: S
- **Dependências**: BE-13 (contratos §6.1-6.2), leitura `api.ts` + `chat.service.ts` existente
- **Subtasks**:
  - [ ] `frontend/src/services/api.ts` lido (baseURL, interceptor)
  - [ ] `frontend/src/services/session.service.ts` criado com 6 métodos `listSessions/create/get/getMessages/delete/update`
  - [ ] `frontend/src/services/chat.service.ts` refatorado `sendMessage(agentId, sessionId, message)` real
  - [ ] `frontend/src/services/agent.service.ts` criado/estendido `listAgents`
  - [ ] `frontend/src/types/` DTOs `ChatSessionDTO`, `ChatMessageDTO`
  - [ ] `npm run build` verde (vue-tsc + vite)

### FE-02 — AgentSelector.vue real

- **Título**: Criar `AgentSelector.vue` dropdown `GET /v1/agents`
- **Descrição prescritiva**:
  - Criar `frontend/src/components/AgentSelector.vue` com `<script setup lang="ts">` (Composition API only, sem Options API). Props `modelValue: string|null` (agentId), emit `update:modelValue`. `onMounted` chama `agentService.listAgents(0,50)` → popula `PrimeVue Dropdown`/`Select` (ver `docs/design/DESIGN_SYSTEM.md` tokens Deep Indigo + Amber). Props `disabled: boolean` (true quando `sessionId` presente — RN2 imutável, mostra agente da sessão desabilitado). Loading `Skeleton`, erro `Message` PrimeVue. V-model `agentId`.
  - Verificar `frontend/src/components/` existentes (ler `TheTopbar.vue` etc.) para padrão PrimeVue.
- **Critério de aceite**: `AgentSelector.vue` renderiza 3 agentes mockados; `GET /v1/agents` chamado em `onMounted`; dropdown contém nomes; desabilitado com `sessionId`; `npm run test:run` com `AgentSelector.spec.ts` verde.
- **CA vinculado**: CA7 (agent selector real, RN2 imutável)
- **Estimativa**: S
- **Dependências**: FE-01 (agent.service)
- **Subtasks**:
  - [ ] `AgentSelector.vue` `<script setup lang="ts">` + PrimeVue `Dropdown`/`Select`
  - [ ] `onMounted` `agentService.listAgents()` → dropdown opções
  - [ ] Props `modelValue` + emit `update:modelValue` + `disabled` quando session existe (RN2)
  - [ ] Estados loading (Skeleton) + erro (Message)
  - [ ] `src/components/__tests__/AgentSelector.spec.ts` com mock `agent.service` (vitest + vue-test-utils)

### FE-03 — ChatSidebar.vue real

- **Título**: Criar `ChatSidebar.vue` lista sessions real com CRUD
- **Descrição prescritiva**:
  - Criar `frontend/src/components/ChatSidebar.vue` com `<script setup lang="ts">` (Composition API). Props `sessions: ChatSessionDTO[]`, `selectedId: string|null`, emits `select(id)`, `newChat`, `delete(id)`. Ou componente autônomo que consome `sessionService.listSessions()` diretamente — PLAN recomenda autônomo com store local para simplificar `Chat.vue`.
  - Layout: `PrimeVue ScrollPanel` lista `title + updateAt` (format `toLocaleDateString`), ordenado DESC (backend já DESC, frontend não reordena). Click item → emit `select`. Botão "Nova Conversa" topo → emit `newChat`. Ação delete por item (ícone `pi pi-trash` `Button` text) → `sessionService.deleteSession(id)` + remove da lista sem reload (CA5). Confirm `ConfirmDialog` opcional. Paginação: `page` state, `loadMore` ou `Paginator` PrimeVue `size 20`. Estados: vazio ("Nenhuma conversa"), loading (`Skeleton` 3 linhas), erro (`Message` + retry).
  - Usar `PrimeFlex` + CSS vars `--p-primary-color` etc. (DESIGN_SYSTEM).
- **Critério de aceite**: `ChatSidebar.vue` consome `GET /sessions`, renderiza title+data DESC, click emite select, "Nova Conversa" limpa, delete remove sem reload, estados vazio/loading/erro, paginado; `ChatSidebar.spec.ts` verde.
- **CA vinculado**: CA3, CA5, CA6
- **Estimativa**: M
- **Dependências**: FE-01 (session.service)
- **Subtasks**:
  - [ ] `ChatSidebar.vue` `<script setup lang="ts">` + `ScrollPanel` + `Button` + `Skeleton`
  - [ ] `onMounted` `sessionService.listSessions(0,20)` → lista DESC
  - [ ] Click item → `getMessages` (via parent `Chat.vue`) + emit `select`
  - [ ] Botão "Nova Conversa" → emit `newChat` (CA6)
  - [ ] Delete ícone → `DELETE /sessions/{id}` + remove da lista sem reload (CA5)
  - [ ] Estados vazio/loading/erro + paginação `Paginator` size 20
  - [ ] `src/components/__tests__/ChatSidebar.spec.ts` com mock `session.service` (render, click, delete, vazio)

### FE-04 — Chat.vue refator (sessionId param, history ASC, Nova Conversa, reload)

- **Título**: Refatorar `Chat.vue` para chat persistente multi-turn
- **Descrição prescritiva**:
  - Ler `frontend/src/views/Chat.vue` existente (SPEC §2.1: sidebar hard-coded, sem sessionId, hard-coded Agent selector) e `frontend/src/router/index.ts` (verificar rota `/chat`).
  - Refator `frontend/src/views/Chat.vue` com `<script setup lang="ts">`:
    - Estado: `sessionId: Ref<string|null>` (persistido em `localStorage 'hive_sessionId'` ou route param `/:sessionId` — escolher localStorage no MVP para CA10 simples), `messages: Ref<ChatMessageDTO[]>`, `input: Ref<string>`, `loading: Ref<boolean>`, `sending: Ref<boolean>`, `selectedAgentId: Ref<string|null>`.
    - Integra `ChatSidebar.vue` (lista sessions) + `AgentSelector.vue` (dropdown) + área mensagens (render `ChatMessage` ASC, user/assistant bubbles, PrimeVue `Card` ou `Avatar`).
    - `onMounted`: `sessionService.listSessions(0,20)` para sidebar; se `localStorage.hive_sessionId` existe → `sessionId.value = stored` → `sessionService.getMessages(sessionId,0,50)` → `messages.value = page.content` ASC (CA10 reload). Se `route.params.sessionId` existir, priorizar route.
    - `selectSession(id)`: `sessionId.value = id; localStorage.setItem('hive_sessionId', id); messages.value = await sessionService.getMessages(id,0,50)` ASC; `AgentSelector` desabilita (RN2).
    - `sendMessage()`: validar `input` 1..4000, `selectedAgentId` not null; `sending=true`; `chatService.sendMessage(agentId, sessionId, input)` → `sessionId.value = resp.sessionId` (se era null, novo id — CA1), `localStorage.setItem('hive_sessionId', resp.sessionId)`, `messages.value.push({role:'user', content:input}, {role:'assistant', content:resp.message})` (ou refetch `getMessages` para garantir ASC); `input=''`, `sending=false`; atualizar sidebar `listSessions` (nova sessão ou `updateAt` topo).
    - `newChat()`: `sessionId.value=null; messages.value=[]; localStorage.removeItem('hive_sessionId')` (CA6); `AgentSelector` re-habilita.
    - `deleteSession(id)`: `await sessionService.deleteSession(id)` → remove da sidebar; se `id===sessionId.value` → `newChat()`.
    - Scroll bottom após send/select (`nextTick` + `scrollIntoView`).
    - Validar `@Size(max=4000)` no input (UX), mas segurança em backend `@Valid`.
    - Loading skeletons, erro toast `useToast`.
  - Atualizar `frontend/src/router/index.ts` se usar `/:sessionId` param (opcional, não bloqueia MVP).
  - Atualizar `frontend/src/stores/auth.store.ts` expose `userId` (decode JWT `JSON.parse(atob(token.split('.')[1]))` ou `GET /me` — verificar `auth.store.ts` existente).
- **Critério de aceite**: `Chat.vue` integra sidebar+selector+messages; `sessionId` null → cria (CA1), com sessionId → append (CA2), sidebar click carrega ASC (CA4), Nova Conversa limpa (CA6), delete funciona (CA5), agent selector real (CA7), reload F5 refaz `GET /sessions` + `GET /messages` e renderiza mesmas 4 mensagens (CA10); `npm run build` verde; Chrome DevTools Network URLs `/hive/v1/chat`, 200/201/204, Console zero erros, sem tela branca.
- **CA vinculado**: CA1, CA2, CA4, CA5, CA6, CA7, CA10
- **RN vinculado**: RN1, RN2, RN3 (window via backend), RN4 (TX split não visível FE)
- **Estimativa**: L
- **Dependências**: FE-01, FE-02, FE-03, BE-13 (API), leitura `Chat.vue` + `router` + `auth.store` existentes
- **Subtasks**:
  - [ ] `Chat.vue` lido + `router/index.ts` + `auth.store.ts` lidos
  - [ ] `Chat.vue` refatorado `<script setup>` com `sessionId`, `messages`, `input`, `loading`
  - [ ] `onMounted` lista sessions + reload CA10 `getMessages` se `localStorage.hive_sessionId`
  - [ ] `selectSession(id)` → `GET /messages` ASC render
  - [ ] `sendMessage()` → `POST /v1/chat` com `sessionId` opcional, armazena retornado, push user+assistant, atualiza sidebar
  - [ ] `newChat()` limpa `sessionId` + messages (CA6)
  - [ ] `deleteSession(id)` + remove lista + `newChat` se ativa
  - [ ] `AgentSelector` integrado, desabilitado com session (RN2)
  - [ ] Scroll bottom, loading skeletons, toast erro
  - [ ] `auth.store.ts` expose `userId` (decode JWT)
  - [ ] `npm run build` verde

### FE-05 — Testes frontend Vitest (ChatSidebar + AgentSelector + Chat + services)

- **Título**: Vitest `__tests__` para FE-02/FE-03/FE-04 + services
- **Descrição prescritiva**:
  - Criar `frontend/src/components/__tests__/ChatSidebar.spec.ts` com `vitest` + `vue-test-utils` + `happy-dom`: mock `session.service.listSessions` (return 2 sessions DESC), `deleteSession`. Testes: `renders list title+data`, `click emits select`, `delete removes item without reload`, `empty state renders "Nenhuma conversa"`, `New Chat button emits newChat`.
  - Criar `frontend/src/components/__tests__/AgentSelector.spec.ts`: mock `agent.service.listAgents` (3 agents), `renders 3 options`, `v-model updates`, `disabled when sessionId exists`.
  - Criar `frontend/src/views/__tests__/Chat.spec.ts` ou `src/components/__tests__/ChatView.spec.ts`: mock `session.service` + `chat.service` + `agent.service`, testes `sendMessage with null sessionId creates session`, `selectSession loads messages ASC`, `newChat clears sessionId`, `reload (mounted with localStorage) loads history` (CA10).
  - Criar `frontend/src/services/__tests__/session.service.spec.ts` com `axios-mock-adapter` ou `vi.spyOn(api, 'get')`: `listSessions calls GET /chat/sessions?page&size`, `deleteSession calls DELETE`.
  - Config `frontend/vite.config.ts` já com `test: {environment: 'happy-dom'}` ou `jsdom` — verificar.
- **Critério de aceite**: `npm run test:run` verde com novos specs; 70% coverage novas funções; sem falhas novas (falhas pré-existentes toleradas mas reportadas).
- **CA vinculado**: CA3, CA6, CA7, CA10
- **Estimativa**: M
- **Dependências**: FE-02, FE-03, FE-04
- **Subtasks**:
  - [ ] `ChatSidebar.spec.ts` com 5+ casos (render, click, delete, vazio, Nova Conversa)
  - [ ] `AgentSelector.spec.ts` com dropdown + disabled
  - [ ] `Chat.spec.ts` com send/select/newChat/reload CA10
  - [ ] `session.service.spec.ts` com axios mock
  - [ ] `npm run test:run` verde, 70% coverage novas funções
  - [ ] `npm run build` (vue-tsc + vite) verde

---

## Resumo de Batches e Gates

| Batch | Tasks | Gate |
|-------|-------|------|
| **Batch 1** | BE-01..06 + INFRA-01 (7 tasks) | `mvn compile -pl agent/impl,agent/dbupdate -am` verde; V5 aplica idempotente |
| **Batch 2** | BE-07..10 + INFRA-02 (5 tasks) | `mvn test -pl agent/impl` 80% service, `when_then` naming, window 20 |
| **Batch 3** | BE-11..14 (4 tasks) | `mvn verify -pl integration-test` `Chat*IT` verde; `fullstack-code-reviewer` zero 🔴 |
| **Batch 4** | FE-01..05 (5 tasks) | `npm run test:run` + `npm run build` verde; Chrome DevTools zero erros; CA10 manual |
| **Total** | **21 tasks** (14 BE + 5 FE + 2 INFRA) | `mvn clean install` raiz zero erros; `workflow-state.json` `code_review: passed` antes `Completed` |

> Mínimo exigido (12 BE + 4 FE + 2 INFRA = 18) superado com 21 tasks para cobrir RF1-RF8 + RN1-RN10 + CA1-CA10 sem gaps.

---

## Checklist de Cobertura (rastreabilidade SPEC → TASKS)

| SPEC ID | TASKS que cobrem |
|---------|------------------|
| RF1 Persistência | BE-01, BE-02, BE-03, BE-04, BE-05, BE-07, BE-08 |
| RF2 Multi-turn history | BE-08, BE-10, FE-04 |
| RF3 POST /v1/chat sessionId opcional | BE-10, BE-11, BE-13, BE-14, FE-04 |
| RF4 Session CRUD | BE-07, BE-08, BE-11, BE-13, BE-14, FE-01, FE-03, FE-04 |
| RF5 Sidebar real | FE-01, FE-03, FE-04, FE-05 |
| RF6 Agent selector | FE-01, FE-02, FE-04, FE-05 |
| RF7 Isolamento tenant | BE-03, BE-07, BE-08, BE-13, BE-14 (CA8) |
| RF8 Auth @HasPermission | BE-13, BE-14 |
| RN1 Título 50 | BE-07, BE-10, BE-14, INFRA-02 |
| RN2 agentId imutável | BE-10, FE-02, FE-04 |
| RN3 Window 20 | BE-08, BE-10, BE-14 |
| RN4 TX split | BE-10, BE-14 |
| RN5 Anti-IDOR 404 | BE-07, BE-08, BE-13, BE-14 |
| RN6 Hard delete cascade | BE-05, BE-07, BE-14 |
| RN7 Validação 400 | BE-11, BE-13, BE-14 |
| RN8 Paginação determinística | BE-07, BE-08, BE-13, BE-14 |
| RN9 Provider isolado | BE-10, BE-14 |
| RN10 Tenant filter toda query | BE-03, BE-07, BE-13 |
| CA1 Criação implícita | BE-07, BE-10, BE-14, FE-04 |
| CA2 Multi-turn LLM | BE-08, BE-10, BE-14, FE-04 |
| CA3 Lista filtrada | BE-07, BE-14, FE-03, FE-05 |
| CA4 History ASC | BE-08, BE-14, FE-04 |
| CA5 Delete cascade | BE-05, BE-07, BE-14, FE-03 |
| CA6 Nova Conversa | FE-03, FE-04, FE-05 |
| CA7 Agent selector | BE-10, FE-02, FE-05 |
| CA8 Isolamento 401/403/404 | BE-13, BE-14 |
| CA9 Validação 400 | BE-11, BE-14 |
| CA10 Reload | FE-04, FE-05 |

---

## Ordem de Delegação ao developer-engineer (via `task` tool)

```
1. BE-01 (VOs) → BE-02 (entities) → BE-03 (repos) → BE-04 (mappers) → BE-05 (V5) → INFRA-01
2. BE-06 (mapper tests) → BE-07 (session service) → BE-08 (message service) → INFRA-02 → BE-09 (unit services) → BE-10 (TX split)
3. BE-11 (DTOs) → BE-12 (DTO mappers) → BE-13 (controllers) → BE-14 (ITs) → code review gate
4. FE-01 (services) → FE-02 (AgentSelector) → FE-03 (ChatSidebar) → FE-04 (Chat.vue) → FE-05 (FE tests) → Chrome DevTools + build gate
```

> Cada task delegável isoladamente; dependências listadas devem estar `passed` antes de delegar a seguinte dentro do batch. Batches sequenciais, mas FE-01 pode iniciar após BE-11 (contratos prontos).

---

## Critério de Pronto Global (DoD SPEC §2.3 + PLAN §9)

- [ ] `mvn clean install` backend zero erros (inclui `agent/api`, `impl`, `dbupdate`, `web`, `integration-test`)
- [ ] `npm run test:run` + `npm run build` frontend verde
- [ ] Chrome DevTools: Network `/hive/v1/chat` 200/201/204, `Authorization` header, Console zero `[ERROR]`/`[WARN]`, UI sem tela branca
- [ ] `fullstack-code-reviewer` zero 🔴, `workflow-state.json` `code_review.last_status: passed`
- [ ] Verify 3 dimensões `VERIFY.md` ✅ (Completeness checkboxes TASKS, Correctness build+tests, Coherence ADRs sem vazamento camada)
- [ ] Brain `brain_store` lições aprendidas + `workflow-state.json` `Completed` após Archive
