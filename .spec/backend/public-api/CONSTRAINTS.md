# CONSTRAINTS — API pública de chat

- **Fase:** dev-planner 1 (Analise)
- **Data:** 2026-09-27
- **Nada aqui é hipótese.** Onde há número, ele foi medido no arquivo citado.

---

## 1. Restrições de plataforma, medidas

### 1.1 `ddl-auto=none` — o DDL é o único que amarra

`application.properties`, `application-dev.properties:29` e `application-prod.properties`
têm `spring.jpa.hibernate.ddl-auto=none`. `open-in-view=false` nos três perfis.

Consequências que valem para **cada** coluna nova desta feature:

- `@Column(length=…)` e `columnDefinition` na entidade são **metadados inertes**. O Hibernate não
  gera e não valida schema. Divergência entre anotação e DDL **não produz erro** — produz mentira em
  código. A regra do projeto: **o DDL é a fonte da verdade; a entidade reflete o DDL.**
- Onde o valor é numérico e o código faz aritmética com ele, `NOT NULL DEFAULT` + `CHECK` de faixa
  **no `CREATE TABLE`**, nunca validação na aplicação. Precedente escrito em
  `.agents/rules/db-migration-flyway.md`: `prompt_template.version` já foi declarado nullable, o
  `UNIQUE` ficou inerte nos NULLs, e `ORDER BY version DESC` (`NULLS FIRST` no PostgreSQL) devolveu a
  linha NULL como "latest" — depois um `Integer` desempacotado em NPE sem mensagem que dissesse a
  causa. **A coluna `pinned_version` desta feature cai exatamente nessa armadilha.** A resolução
  correta é a mesma do precedente: CHECK que amarre o par `(pin_mode, pinned_version)`.
- Nenhuma migration pode conter delimitador `$$` (dollar-quote), **nem em comentário** — o parser de
  script do Flyway corta no delimitador e o arquivo passa a ser SQL inválido. Isso já custou 3 erros
  de PL/pgSQL no Batch 2 (registrado em `workflow-state.json → batch2_assistant.achados_durante_execucao`).
- **`pin_mode` (D1):** o `DEFAULT 'FOLLOW_ACTIVE'` no DDL **não** é usado pelo provisionamento — o
  `CommandLineRunner` (10.5) informa `pin_mode` **explicitamente** em toda linha. O DEFAULT só atende
  INSERT fora do runner (seed, teste, SQL manual). Motivo do valor: `PINNED` torna todo `PUT` de
  rotina do prompt um 409 para a integração (ver `RUNBOOK-prompt-edition.md`). O trio
  `(pin_mode, pinned_version)` continua amarrado pelo `CHECK` — `PINNED` sem versão é **recusado pelo
  banco**, e qualquer texto que afirme o contrário está errado.
- **Tetos de payload (D2):** `max_messages`/`max_message_chars`/`max_total_chars` são colunas com
  `NOT NULL DEFAULT` + `CHECK` de faixa, reclamadas no claim `plim`. Onde o código trata o valor
  como número, a garantia fica no schema, não no chamador — mesma lição do
  `prompt_template.version`.

### 1.2 O `repair()` do Flyway — e por que o módulo novo NÃO deve copiar o padrão

Estado real por módulo:

| Módulo | Location | History table | `baselineOnMigrate` | `outOfOrder` | `repair()` | Reescrever sem `DROP SCHEMA` |
|---|---|---|---|---|---|---|
| `agent` | `classpath:/db/migration/agent` | `flyway_schema_history_agent` | `true` | **`true`** | **sim** | 🔴 **0 migrations, sucesso reportado** |
| `identity` | `classpath:/db/migration/identity_db` | `flyway_schema_history_identity` | `true` | — | **sim** | 🔴 **0 migrations, sucesso reportado** |
| `tenant` | `classpath:/db/migration/tenant_db` | `flyway_schema_history_tenant` | `true` | — | **não** | 🟢 morre com `Migration checksum mismatch` |

O `repair()` **não executa SQL**: realinha checksum e descrição das migrations **já aplicadas** e marca
como `deleted` as versions que sumiram. Medido com Flyway 10.10.0: série nova colidindo em número de
versão com série antiga gravada → `migrationsExecuted = 0`, `Successfully validated 12 migrations`,
**boot verde com o schema antigo**. O `repair()` transforma um erro barulhento em mentira silenciosa.

**A decisão de projeto que sai daqui, e ela é o oposto da regra existente:**

> O novo módulo `publicapi` **não chama `repair()`**.

Justificativa: é o único módulo cujo histórico Flyway **nasce vazio**. Não há checksum applied para
realinhar e nenhuma version anterior para marcar `deleted`. Com `repair()` ausente, qualquer
divergência futura de checksum — um `.sql` editado depois de aplicado, um jar velho subindo — morre
**alto e verboso**, que é o comportamento correto para um módulo cujo histórico é pequeno e cabe na
cabeça de uma pessoa. Copiar o padrão dos outros três seria comprar um problema que este módulo não
tem.

Também por isso `outOfOrder` **não** é ligado: a série nova é contígua e nasce do zero. `outOfOrder`
só existe no `agent` porque o histórico dele acumulou `V2..V10` e depois `V14`.

**Faixas de versão livres, medidas:**

| Módulo | Versions existentes | Próxima livre |
|---|---|---|
| `agent` | V2, V3, V4, V5 | **V6** |
| `identity` | V2, V3 | **V4** |
| `tenant` | V2, V3 | **V4** |
| `publicapi` (novo) | — | **V1** ← desvio deliberado de "começar em V2" |

Sobre começar em V1: a regra "V1 é baseline pré-existente" vale para os três módulos existentes, que
têm tabelas no banco sem histórico. O `publicapi` nasce com histórico vazio e tabelas novas — `V1` é
a primeira e não há colisão possível, porque nada foi gravado ainda. O desvio é seguro **e**
verificável: se a V1 rodar duas vezes, o Flyway acusa `checksum mismatch` alto, não finge sucesso.

### 1.3 O JAR é o que sobe, não o `src/`

`mvn spring-boot:run -pl bootstrap` resolve os módulos irmãos do **repositório Maven**, não da
árvore de fontes. Editar `V1__public_api_client.sql` e subir sem rebuild aplica o SQL antigo e reporta
`"Successfully applied N migrations"`. Já aconteceu neste projeto (registrado na seção de
armadilhas do `ADR-013` e na regra de Flyway).

**Regra para o PLAN: `mvn clean install -DskipTests` antes de qualquer verificação com banco, e
confirmação no `psql`, nunca no log.**

### 1.4 Java com `--enable-preview` — `ScopedValue` é load-bearing

`backend/pom.xml:37` (`<argLine>@{argLine} --enable-preview`) e `:95` (compiler). O
`TenantContext` usa `java.util.ScopedValue`:

```
private static final ScopedValue<UUID> CURRENT_TENANT = ScopedValue.newInstance();
public static UUID getCurrentTenant() { return CURRENT_TENANT.orElseThrow(TenantNotFountException::new); }
public static <R> R runWithTenant(UUID t, Callable<R> task) { return ScopedValue.where(CURRENT_TENANT, t).call(task); }
```

Consequências para esta feature:

- O `runWithTenant` **não propaga** para thread pool comum (é `ScopedValue`, não `InheritableThreadLocal`).
  O `@Async`, o `@Async` do Spring e o agendador **não herdam o tenant**. Nenhum código novo pode
  disparar a chamada ao provider fora do `runWithTenant`, senão a leitura da configuração do tenant
  estoura `TenantNotFountException` — **ou pior**, roda sem filtro e lê cross-tenant.
- `TenantContext.getCurrentTenant()` **lança** quando ausente, em vez de devolver `null`. Qualquer
  caminho novo precisa de `TenantContext` **ou** de uma exceção de domínio explícita. Não há
  "sem tenant, segue o default".
- `--enable-preview` precisa estar no `argLine` do surefire **e** do compiler. Um módulo novo que
  introduza o flag em um lugar só quebra o outro silenciosamente (o teste passa, o build falha).

### 1.5 Isolamento de tenant: `@Filter` + `TenantAspect`, e as duas brechas

```
AbstractEntity
  @FilterDef(name="tenantFilter", parameters=@ParamDef(name="tenantId", type=UUID.class))
  @Filter(name="tenantFilter", condition = "tenant_id = :tenantId")
  protected UUID tenantId;                    // nullable=true, updatable=false

TenantAspect  @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..)) || ...JpaSpecificationExecutor+.*(..)")
  → session.enableFilter("tenantFilter").setParameter("tenantId", TenantContext.getCurrentTenant())
```

Fatos que restringem o desenho:

1. **O filtro é ligado por um `@Before` em cima de métodos de `JpaRepository`.** Qualquer repositório
   novo que **não** estenda `JpaRepository`/`JpaSpecificationExecutor` **não** recebe o filtro. E uma
   entidade nova que **não** estenda `AbstractEntity` **não** tem `@Filter` declarado — mesmo com
   `tenant_id` na tabela, o Hibernate não filtra.
2. **O `catch (TenantNotFountException)` engole a ausência e segue com o filtro desligado.** É
   deliberado (fluxo de registro) e é o que torna o endpoint de token um caminho *sem* filtro de
   tenant. Ver `EXPLORATION.md §4.4`: por isso `client_id` tem de ser globalmente único e o tenant
   tem de vir da linha da credencial, nunca do request.
3. `AbstractEntity.tenantId` é `nullable=true` — **errado para 5 das 6 tabelas que o exigem e certo
   para `users`**. É débito registrado e **não** deve ser tocado aqui: endurecer o superclasse
   trocaria uma mentira por outra, e o isolamento real vem do `@Filter` + do `NOT NULL` no DDL. A
   correção de fundo é por entidade, em batch próprio.
4. As 3 tabelas novas **precisam** estender `AbstractEntity` (o que as inclui no `@Filter` e no
   `TenantListener`) e declarar `tenant_id UUID NOT NULL` no DDL.
5. `TenantListener.setTenant` só preenche `tenantId` se ainda for `null`, e engole
   `TenantNotFountException`. Ou seja: **o isolamento de escrita também depende do `TenantContext`
   estar correto**, e um `@PrePersist` sem contexto grava `tenant_id` nulo numa coluna `NOT NULL` —
   o banco recusa, e a recusa é o sintoma, não o diagnóstico.

### 1.6 `ChatTurnGateway` e o contrato `TurnReply` — o que a porta permite e o que não

`TurnReply.Status{ANSWERED, EMPTY_RESPONSE, FAILED}`. Falha é **retorno**, não exceção, e
`failure()` carrega a exceção do provider **desembrulhada e inalterada** (`TurnReply.java`:
`TurnReply.failed(...)` + `Objects.requireNonNull(failure)`).

Três restrições concretas para o chamador público:

- `LangChain4jTurnGateway.buildMessages` coloca o `template` do prompt como `SystemMessage` **primeiro**,
  depois `addAll(request.conversation())`. Ordem de precedência fixa. Um `system` dentro de
  `conversation` chegaria **depois** do template e seria a última instrução do modelo — o que
  **substitui** o prompt do cliente na prática. É a razão técnica de Q2 (rejeitar `system`).
- `execute()` passa `request.sessionId()` para
  `llmClientFactory.createClientWithSession(provider, model, temp, maxTokens, topP, apiKey, baseUrl, sessionId)`.
  O `LlmClientFactory` tem **default** que delega a `createClient(...)` (sem sessão) —
  `createClientWithSession` é `default` na interface. Então `sessionId = null` é **aceito** pelas
  fábricas que não têm conceito de sessão. **A verificar no PLAN:** as implementações de
  `OPENCODE_GO`/`OPENCODE_ZEN` sobrescrevem esse default para mandar o header
  `x-opencode-session`; um header com valor `null` pode chegar ao provider como `"null"`. **Gate de
  verificação, não suposição.**
- `TurnRequest.promptTemplate` é `requireNonNull` **na construção**. Um fluxo público sem template é
  erro em tempo de construção — que é o comportamento desejado, e vale como teste.

### 1.7 `ChatServiceImpl` — o que é reusável e o que não é

Medido: **345 linhas, 9 dependências de construtor** (o `workflow-state.json` registra "331 linhas /
11 deps" — número desatualizado, o que importa é que hoje são 9 e o arquivo tem 345 linhas).

O que ele faz, decomposto:

| Trecho | Responsabilidade | Reusável num fluxo stateless? |
|---|---|---|
| `:123,:127` | Lê `TenantContext.getCurrentTenant()` e `SecurityUtils.currentUserId()` do **contexto ambiente** | ❌ **É a trava.** Um chamador não-humano não tem `userId` |
| `:136-138` | `chatRateLimiter.check(tenantId)` | ❌ quota é por `clientId` (decisão 8) |
| `:143-168` | Cria ou carrega `chat_session`; ignora `promptTemplateId` divergente | ❌ stateless |
| `:170-174` | `chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)` | ❌ **scopado por sessão** |
| `:176-186` | `tenantLlmConfigService.require` → `VendorCatalog.validate` → `credentialResolver.resolve` | ✅ **política pura, sem sessão** |
| `:188-194` | TX1: persiste mensagem do user; lê histórico; converte para o tipo do provider | ❌ persiste |
| `:200-201` | `turnGateway.execute(new TurnRequest(...))` | ✅ **é a porta** |
| `:203-246` | Os 3 ramos de status + `FailureClassifier` + evict de discovery + `422`/`502` | ✅ **política pura, sem sessão** |
| `:248-252` | trace + TX2 (assistant + touch) | ❌ persiste; `ChatTrace.sessionId` seria `null` |
| `:328-344` | `toProviderMessages(history)` — a conversão `ChatMessage` → tipo do provider | ✅ **é a mesma conversão, com outra fonte** |

**Conclusão medida, não opinada: `chat()` tem três responsabilidades soldadas** — (1) resolver o
prompt de uma sessão, (2) rodar o turno, (3) persistir e traçar. A (2) **já foi extraída** (é a
porta, ADR-012). A (1) é intrinsically session-scoped. A (3) é session-scoped por construção
(`chat_message.content NOT NULL`, split TX1/TX2).

→ **Nenhuma linha de `ChatServiceImpl` deve ser reusada, e nenhuma precisa ser.** O que falta extrair
**não é a linha de código, é a decisão** — duas collaboration que ainda estão inline dentro do método
que também faz TX:
- **`ProviderResolution`** (`:176-186`) — resolve config do tenant, valida vendor, resolve credencial.
- **`TurnOutcomeMapper`** (`:203-246`) — mapeia os 3 status para a exceção/trace/evict certos.

Extrair essas duas é o que torna a API pública **aditiva** em vez de fork, e encolhe
`ChatServiceImpl`. É a **mesma jogada que o ADR-012 fez um nível acima**: mover política já decidida
para fora da classe que também faz trabalho de sessão. **Não é reescrita e não muda comportamento**
— e a lição do ADR-013 ("construir a segunda capacidade antes do primeiro loop estar fechado") exige
exatamente esse cuidado: as mudanças precisam ser verificadas por **execução**, não por mock.

### 1.8 O envelope de erro existente é `SNAKE_UPPER`, e o contrato público pede `snake_lower`

`ControllerExceptionHandler.baseException` deriva o código **do nome da classe**:
`toSnakeUpper(e.getClass().getSimpleName().replace("Exception",""))` — `PromptNotActiveException` →
`PROMPT_NOT_ACTIVE`. E é `application/problem+json` (RFC 7807), com `type/title/status/detail/instance/code/timeStamp`.

O contrato público pede `error.code` **snake_lower** no formato OpenAI (`prompt_not_active`), para o
ERP poder tratar por código. São dois envelopes incompatíveis, no mesmo `context-path`.

**Restrição: um `@RestControllerAdvice` global não pode atender os dois.** O beans
`ControllerExceptionHandler` é `@RestControllerAdvice` sem qualifier e já captura `Exception` — ele
vence qualquer advice mais específico na ambiguidade de handler. Solução: o advice público com
`@RestControllerAdvice(basePackages = "…publicapi.web…")` **e** os handlers do global gaining
`@Order(Ordered.LOWEST_PRECEDENCE)`. Ou, mais simples e mais explícito: **sem `@RestControllerAdvice`
para o público** — o controller público traduz as exceções de domínio em resposta OpenAI dentro de
um `@ExceptionHandler` local. Menos mágico, mais código, zero risco de ordenação de handler.

### 1.9 Cifra: `AesGcmAttributeConverter` existe, e **não** é a ferramenta certa aqui

`AesGcmAttributeConverter` (`agent/impl/.../converter/`): AES-256-GCM, `GCMParameterSpec` 128 bits,
IV de 12 bytes aleatório por cifragem, master key de 32 bytes de `ProviderMasterKey.MASTER_KEY_ENV`
(`PROVIDER_MASTER_KEY`) com fallback para `provider.master.key`, e um `testKeyOverride` estático
**protegido por `requireTestProfile()`** (só com profile `test`).

É cifra, e a master key é **a mesma** que protege as credenciais de provider. O `client_secret` **não
deve** ser cifrado com ela:

- O segredo precisa ser **verificável**, não recuperável. Cifrar com a master key cria uma chave
  simétrica cujas duas metades estão no mesmo processo e no mesmo banco — o que não é defesa.
- Cifrar coloca o `client_secret` no mesmo caminho de `MISMATCH_MESSAGE` (trocar a master key
  inutiliza **todas** as credenciais). O `client_secret` é gerado por integração e rotacionado por
  integração; acoplar seu ciclo de vida ao da master key é uma dependência desnecessária.
- A referência de mercado é explícita: segredo de **alta entropia** é SHA-256, não bcrypt —
  "SHA-256 é apropriado aqui porque API keys já são segredos de alta entropia. bcrypt é para senha de
  baixa entropia. Para segredos aleatórios de 256 bits, SHA-256 é rápido e suficiente — a chave não é
  adivinhável a partir do hash, porque o espaço de chaves é grande demais para brute force."

**Precedente no projeto:** `SecurityTokenServiceImpl:195-208` (`generateRefreshTokenValue`) gera 64
bytes de `SecureRandom`, codifica base64url, e faz `SHA-256` → hex. A **primitiva** (SHA-256 de
segredo de alta entropia) é o precedente a copiar.

⚠️ **A escolha que NÃO copiar:** aquele método **devolve o hash ao cliente** e usa o hash como chave
do Redis. Isso vale para refresh token (curto, 14 dias, rotação a cada uso). Para `client_secret`
(longo, meses) seria um retrocesso: um dump do banco entregaria o segredo em si, não um resumo.
O desenho correto: **gerar raw → devolver raw ao cliente uma única vez → armazenar
`SHA-256(raw)` → verificar hasheando o segredo apresentado e comparando em tempo constante.**

### 1.10 Cota: o precedente que existe e o que ele não cobre

`ChatRateLimiter` (`agent/impl`): `@Component`, `MAX_CALLS=10`, `WINDOW_SECONDS=60`, chave = `tenantId`,
delegando a `SlidingWindowRateLimiter` (`commons/impl`):
`ConcurrentHashMap<UUID, Deque<Instant>>`, `synchronized(deque)`, **sem eviction de chaves**.

- `SlidingWindowRateLimiter` é **já parametrizado** por `maxCalls` e `windowSeconds` — o algoritmo e a
  janela são injetados. Portanto a "cota é dado da credencial" (decisão 8) **não exige** um algoritmo
  novo, só uma fonte de limite diferente e uma chave diferente (`clientId`, não `tenantId`).
- Três defeitos medidos: (a) contagem por JVM → limite efetivo `10N/min` sob N instâncias;
  (b) `ConcurrentHashMap` sem eviction → crescimento não limitado com o número de tenants;
  (c) chave é `tenantId`, o que **impõe** teto por tenant mas **não permite** cota por credencial.
- `ValidateRateLimiter` existe com o mesmo padrão (mesma classe base, outro limite).

**Decisão (`EXPLORATION.md §4.5`):** a cota pública nasce em Redis, e o `ChatRateLimiter` em memória
**não é tocado**. Duas classes, o mesmo *shape* de interface, nenhum estado compartilhado.

## 2. Restrições de não-escopo (o que esta feature **não** vai fazer)

Fechado pelo usuário e pelo ADR-013. Nenhum destes entra como "melhoria futura dentro do batch":

RAG · tools · memória de longo prazo · anexos/arquivo · voz · streaming (SSE) · billing · metering ·
auto-serviço de provisionamento · SDK · marketplace de prompts · **matriz de papéis de usuário
(RBAC completo)** · cliente agente (fase 2).

**D6 (revisão de 2026-09-27): "sem auto-serviço" ganhou forma concreta — não há tela.** O
provisionamento é `CommandLineRunner` + `ProvisioningPayloadBuilder` em código. `ux-designer` **não**
é obrigatório nesta feature. Gatilho para voltar: 2 credenciais de produção, ou alguém sem acesso ao
ambiente precisar provisionar — aí o parecer de UX é **gate do batch**.

O ADR-013 §"O que este ADR não decide" nomeia explicitamente esta feature:
*"**APIs públicas** — modelo de autenticação e cota, indefinido. É a próxima decisão de produto."*
Este documento é essa decisão.

## 3. Restrições do harness (o que a entrega precisa provar)

De `.agents/rules/harness-continuous.md`, porque uma API externa falha de um jeito que nenhum teste
unitário pega:

| Gate | O que esta feature precisa |
|---|---|
| **H1** — suíte completa | `mvn clean install -DskipTests` → `mvn test` → `mvn verify -pl integration-test -Pintegration -am`. **`-am` é obrigatório** (sem ele o Maven consome jar stale do `~/.m2` e "passa" com código antigo). O perfil `integration` **desliga o surefire**: `mvn clean install -Pintegration` roda **zero** unitários. |
| **H1.2** | Qualquer teste falhando, **novo ou pré-existente**, bloqueia. Sem `@Disabled`, sem `assumeTrue`, sem comentário de skip. |
| **H2** | Baseline antes de começar: a suíte precisa estar verde. Se houver falha pré-existente, isso é P1 e sai antes desta feature. |
| **H3** — E2E | Esta é a **primeira** feature cujo consumidor é **externo ao browser**. O H3 (re-cadastro via UI) não a cobre: o que ela exige é o **gate de conexão com o ERP** — ver `VALIDATION.md §4`. |
| **H5** | Processos `:8080`/`:5173` encerrados ao fim. |
| **H6** | `fullstack-code-reviewer` após a última mudança, zero 🔴, `brain_store` executado, `workflow-state.json` atualizado. |
| **TRUNCATE** | `DatabaseCleaner.reset()` **descobre as tabelas por `pg_tables`** excluindo `flyway_schema_history%` — as 3 tabelas novas entram no truncate **automaticamente**. Nenhuma edição é precisa. |

## 4. Compliance

### 4.1 Onde o segredo vive, e qual é a resposta

O `client_secret` vive no **CI/CD do ERP**, em secret store. Isso é conformidade **do ERP**, e a
resposta do Hive à pergunta *"como vocês guardam meu segredo"* é a **due diligence** deles. O que o
Hive controla é o outro lado:

| Controle | Onde |
|---|---|
| Segredo nunca gravado em claro | `SHA-256` no banco, comparação em tempo constante |
| Segredo exibido **uma vez** só | Somente no ato do provisionamento; a resposta traz o raw, o banco guarda o hash |
| Segredo nunca em log | `toString()` de todo record novo que o carregue precisa de override (precedente: `TurnRequest.toString()` mascara `apiKey`, `TurnReply.toString()` não renderiza a falha) |
| Segredo nunca em resposta de erro | `ControllerExceptionHandler.dataIntegrityViolation` já tem o precedente de mapear constraint por **literal escrito à mão**, nunca pela mensagem do driver (OWASP A05) |
| Transporte | TLS 1.2+ obrigatório em produção (`.agents/rules/security-standard.md` A02) |
| `Authorization` vs body | OAuth 2.0 recomenda `client_secret_basic` **no header**, porque os access logs costumam registrar o body e filtrar headers. O endpoint aceita os dois; o ERP usa `basic` |
| TTL curto | 10 min. Vazamento de token tem janela de 10 min, não de 14 dias |
| Revogação | `client` desativado → efetivo imediato (Redis, §`ARCHITECTURE.md §6`); grant de prompt removido → efetivo ≤ TTL do token |
| Rate limit no endpoint de token | Um `client_secret` sendo forjado em alta taxa é brute force. `POST /auth/token` **precisa** de limite próprio, separado da cota de chat |

### 4.2 Autorização — o que a allowlist não cobre, e o que fecha

A allowlist controla **qual prompt**, não **quais endpoints**. Com as 6 permissões hardcoded, um token
alcança `AGENT_MANAGE` — que em `provider-credential` e `tenant/llm-config` significa **trocar a chave
de API e o modelo default do tenant**.

Três listas literais independentes, confirmadas:

| Onde | O quê |
|---|---|
| `SecurityTokenServiceImpl:182` | `List.of("PROMPT_MANAGE","PROMPT_ADMIN","AGENT_MANAGE","AGENT_ADMIN","CHAT_SEND","CHAT_SESSION_MANAGE")` — a que **concede** |
| `SecurityConstants.DEFAULT_PERMISSIONS` | as mesmas 6 — **só exibição**, nada lê no caminho da concessão |
| `RoleControllerV1` | literais inline (`ADMIN` com 4 permissões, `USER` com 2) — só exibição |

**Não existe modelo de dados de papéis.** Nenhuma entidade, nenhuma tabela, nenhum repositório ligado
a role/permission/grant. `HasPermissionAspect` lê as autoridades do `Authentication` (que o
`SecurityFilter` montou do claim `permissions` do token) e a negação **funciona e é testada**
(`ValidateIT.when_validate_withoutPermission_then_returns403`).

**Como fecha, sem pagar RBAC de usuário (restrição do PM, honrada):**

1. **Cadeia de segurança separada** para `/v1/public/**` (`EXPLORATION.md §4.2`). O filter chain
   público não contém nenhum endpoint interno, então um token público **não tem como** alcançar
   `/v1/prompt-templates` — não por lista, por **ausência de rota naquele chain**. Isso é
   *fail-closed por construção* e é mais forte do que qualquer lista.
2. **Grant model persistido** (P-a): as concessões saem de linha de banco, não de literal. Uma
   credencial pública recebe, por padrão, **exatamente uma concessão** — `PUBLIC_CHAT_COMPLETE` —
   e nada mais. Conceder mais é ato explícito e auditável.
3. `aud` distinto (`hive.public` vs `hive.web`) impede que um token do painel web seja aceito na API
   pública e vice-versa, mesmo com a mesma assinatura HMAC e o mesmo segredo.

**O que permanece débito, honestamente:** o RBAC de **usuário** continua decorativo. Todo usuário que
se autentica continua sendo superusuário. Esta feature **não** paga nem corrige isso — e o
`.spec/tech-debts/security-rbac/TASKS.md` P1-1 continua aberto. A mitigação declarada é que a
superfatoração de usuário é um problema **interno** (acesso ao painel), e a superfície pública nova é
**fechada por cadeia**, não por lista.

### 4.3 Exceções ao `security-standard.md` — registradas, não escondidas (D8)

Veredito do `security-engineer`: das 4 exceções apontadas, **nenhuma sai com risco aceito sem
mitigação**, e 2 **não são exceção** (esticar a norma seria scope-stretch):

| # | Item | Veredito | Ação |
|---|---|---|---|
| 1 | HMAC256 simétrico no token público | **Exceção ACEITA**, registrada | Risco: `iss`/`aud`/`exp` são campos que o **atacante** escreve. Gatilho para revisitar: 2º cliente externo, ou `kid`/rotação |
| 2 | Chave única para token humano e de máquina | **Correção barata (~10 linhas), NÃO é exceção** | `api.security.public.token.secret` + `decodeAndVerify` por parâmetro + JUnit dos dois sentidos. Sem isso, `aud` separa só o token **honesto** |
| 3 | TLS não terminado no processo Java | **Não é violação como escrita** (a norma fala de dados em trânsito) e **incomprovável no repo** (`grep server.ssl` = 0) | Correção: falhar o boot em prod sem `HIVE_TLS_MODE` explícito. Prova é do edge = débito com owner de ops |
| 4 | Perfil `dev` como default do boot | **Correção barata (~10 linhas)** | `prod` já é fail-fast (`${JWT_TOKEN}`); o furo é o caminho que ninguém escolhe: sem `SPRING_PROFILES_ACTIVE`, o boot **sucede** com chave de 6 bytes. Segredos de dev versionados **não** são exceção (placeholders documentados) |
| 5 | `/v3/**` e `/actuator/**` em `permitAll` | **Correção barata**, e o caso é pior: springdoc é **COMPILE** de bootstrap | `springdoc.api-docs.enabled=false` em prod + allowlist explícita de actuator + IT |

⚠️ Autocrítica do `security-engineer`, registrada: *"`DataIntegrityViolationException` ecoa dado
interno"* estava **ERRADO** (mapeia constraint por literal, manda a mensagem do driver só para o
log); *"teto de corpo 160 KB"* era falso (`\uXXXX` = 6 bytes/char → 960 KB, teto 1 MiB); e
*"HMAC é um risco"* são **dois** (algoritmo = exceção; chave única = correção).

## 5. Dívidas registradas que esta feature **não** corrige

| Dívida | Arquivo | Efeito nesta feature |
|---|---|---|
| `/actuator/**` em `permitAll` | `SecurityConfig.java:44` | 🟡 **Adjacente.** A cadeia nova casa só `/v1/public/**`, então actuator fica como está. Correção barata em código (D8): `management.endpoints.web.exposure.include` explícito + IT |
| `/v3/**` em `permitAll` | `SecurityConfig.java:44` | 🔴 **Pior do que o parecer original dizia.** `springdoc-openapi-starter-webmvc-scalar` é dependência **COMPILE** de `bootstrap/pom.xml:53-57` — `/v3/api-docs` está no jar de **prod** e publica o schema de **todos** os endpoints internos, inclusive `POST /hive/v1/provider-credential`. Correção barata (D8): `springdoc.api-docs.enabled=false` em prod + IT. O enquadramento antigo ("adjacente, não afetado") era certo para actuator e **falso** para /v3 |
| RBAC decorativo | `.spec/tech-debts/security-rbac/` P1-1 | 🔴 Aberto. Não pago aqui. |
| `PROMPT_MANAGE` barra a leitura | `.spec/tech-debts/prompt-read-permission/` | 🟢 **Confirmado irrelevante.** O caminho novo resolve o template por `code` **em serviço**, por dentro do `@Filter` de tenant, e **não passa** por `PromptTemplateControllerV1:64`. O bloqueio continua existindo para a UI — que é a intenção do débito (produto, não segurança). |
| `ChatSessionEntity.summary` TEXT vs DDL `VARCHAR(2000)` | `db-migration-flyway.md` #2 | 🟢 Não toca. |
| `ChatMessageEntity.role` length 20 vs DDL 20 | `db-migration-flyway.md` #3 | 🟢 Já alinhado. |
| `AbstractEntity.tenantId nullable=true` | `db-migration-flyway.md` #5 | 🟡 **As 3 tabelas novas declaram `NOT NULL` no DDL** e são `AbstractEntity`. A divergência do superclasse fica registrada, não corrigida — ver §1.5.3. |
| `sliding-window` em memória, sem eviction | `agent/impl` | 🟡 **Contornado** para a rota pública (Redis). O `/v1/chat` interno fica como está. |
| `ChatTrace` 1 linha por turno | `agent/api` | 🟢 Reusado. `sessionId` é `null` no fluxo público — isso é **fato a registrar na linha de log**, não a esconder. |

---

**Próxima fase:** `DESIGN-DECISIONS.md` — confronto do desenho, inclusive **contra** a premissa do briefing.
