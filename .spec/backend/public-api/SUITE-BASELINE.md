# SUITE-BASELINE — API pública de chat (BATCH 0, task 0.1)

- **Data da medição:** 2026-10-09, 21:08–21:16 BRT (UTC−03:00)
- **Commit medido (HEAD):** `e6d289e033efbeeb0fd1dbcd2fe6b9ffc4aabad8`
- **Feature:** `public-api-chat` (ADR-014 em `docs/adr/ADR-014-API-Publica-Chat.md`)
- **Resultado:** **PASS** — 0 falhas, 0 erros, 0 skipped em backend unit, backend IT e frontend.

> Este arquivo é a **única fonte da verdade** de contagem. Os 3 artefatos de planejamento tinham
> **3 números divergentes** (376/76/158 · 505/118/222 · 341/63/177). A medição abaixo foi feita agora,
> contra o HEAD acima, e substitui os três. Nenhum código de feature foi escrito neste batch.

---

## 1. Contagens medidas

| Suíte | run | passed | failed | errors | skipped | Classes | Arquivos |
|-------|----:|-------:|-------:|-------:|--------:|--------:|----------|
| Backend unit | **371** | 371 | **0** | **0** | **0** | 44 | 10 módulos |
| Backend IT (Testcontainers) | **75** | 75 | **0** | **0** | **0** | 10 | 1 módulo |
| Frontend (Vitest) | **189** | 189 | **0** | — | **0** | 24 arquivos | — |

**Baseline canônico: `371 / 75 / 189`** (backend unit / backend IT / frontend).

Comparação com os números que estavam nos artefatos — todos estavam errados:

| Artefato | unit | IT | frontend |
|---|---|---|---|
| artefato A | 376 | 76 | 158 |
| artefato B | 505 | 118 | 222 |
| artefato C | 341 | 63 | 177 |
| **medido agora (HEAD `e6d289e`)** | **371** | **75** | **189** |

Os 9 números anteriores **não sobrevivem**. A medição é o baseline; número de planejamento é pista.

---

## 2. Comandos usados (exatamente os executados)

```bash
# 1. Dependências
docker compose -f docker/docker-compose-vetor.yml up -d     # → postgres (projeto hive) :5433 healthy
docker compose -f docker/docker-compose-redis.yml up -d     # → CONFLICT documentado, redis já no ar

# 2. Suite completa backend — NÃO `mvn clean install -Pintegration` sozinho:
#    o perfil `integration` seta <skip>true</skip> no surefire e roda ZERO unitários.
cd backend
mvn clean install -DskipTests               # → BUILD SUCCESS (37 módulos, 30.3 s)
mvn test                                     # → BUILD SUCCESS
mvn verify -pl integration-test -Pintegration -am   # → BUILD SUCCESS  (-am OBRIGATório)

# 3. Baseline frontend
cd frontend
npm run test        # → vitest run: 24 files, 189 passed
npm run build       # → vue-tsc -b && vite build: ✓ built
```

Saída completa preservada em `/tmp/opencode/hive-unit.log`, `/tmp/opencode/hive-it.log`,
`/tmp/opencode/hive-fe-test.log`, `/tmp/opencode/hive-fe-build.log` (efêmero — não versionado).

---

## 3. Detalhe por suíte

### 3.1 Backend unit — `mvn test`

**BUILD SUCCESS · 371 passed / 0 failed / 0 errors / 0 skipped**

Módulos com testes (classes):

| Módulo | Classes | Testes |
|--------|--------:|-------:|
| `agent/impl` | 18 | 192 |
| `agent/web/internal/v1` | 4 | 21 |
| `commons/api-tools` | 2 | — |
| `commons/impl` | 2 | — |
| `identity/impl` | 4 | 19 |
| `provider/api` | 2 | — |
| `provider/langchain4j` | 3 | 54 |
| `security/impl` | 4 | 15 |
| `tenant/api` | 1 | — |
| `tenant/impl` | 4 | — |

### 3.2 Backend IT — `mvn verify -pl integration-test -Pintegration -am`

**BUILD SUCCESS · 75 passed / 0 failed / 0 errors / 0 skipped**

| Classe IT | run |
|---|---:|
| `ChatEmptyResponseIT` | 7 |
| `ChatSessionIT` | 11 |
| `CredentialIT` | 11 |
| `LoginIT` | 4 |
| `MasterKeyDeclarativePathIT` | 4 |
| `NoResourceIT` | 3 |
| `OpencodeGoZenIT` | 13 |
| `SmokeIT` | 3 |
| `TenantLlmConfigIT` | 11 |
| `ValidateIT` | 8 |

O ambiente dos ITs **não é** o postgres de dev: `WebIntegrationTest` é `@ActiveProfiles("test")` e
fornece datasource e Redis via `@DynamicPropertySource` com **Testcontainers**
(`pgvector/pgvector:pg16` + `redis:7-alpine`). Portanto as contagens acima são independentes dos
containers de dev deste batch.

> **Nota sobre linhas `ERROR` no log do IT (36 ocorrências).** Não são falhas de teste. São saídas de
> log da própria aplicação exercitando caminhos de erro (`ControllerExceptionHandler`,
> `AesGcmAttributeConverter` com chave mestra errada de propósito, `CredentialMasterKeyStartupCheck`).
> `grep` por `FAIL|<<< FAILURE|<<< ERROR|Failed tests` nos dois logs: **zero ocorrências**.

### 3.3 Frontend — `npm run test` + `npm run build`

**Testes: 24 arquivos, 189 passed, 0 failed.**
**Build: `vue-tsc -b` (typecheck) + `vite build` → `✓ built`, exit 0.**

---

## 4. Estado do ambiente no momento da medição

| Item | Valor observado |
|---|---|
| HEAD | `e6d289e033efbeeb0fd1dbcd2fe6b9ffc4aabad8` |
| Java | **OpenJDK 25.0.2** — ⚠️ `AGENTS.md` declara Java 21; a medição rodou em 25 |
| Maven | 3.9.10 |
| Node | v26.7.0 |
| Vite | 8.3.4 |
| Postgres dev (projeto `hive`) | criado neste batch, `postgres` em `:5433`, healthy, db `hive` |
| Postgres estrangeiro | `postgres-pgvector` em `:5432` (projeto `docker`, volume `postgres_postgres_data`) |
| Redis | `hive-redis` já no ar em `:6379` (projeto `docker`, volume `docker_redis_data`) |

### 4.1 O `docker compose up` do Redis falhou — e não é falha de teste

`docker-compose-redis.yml` declara `container_name: hive-redis`, e um container com esse nome **já
existia** sob o projeto `docker`. O Compose recusa reutilizar o nome global:

```
service:redis:1 Error response from daemon: Conflict. The container name "/hive-redis"
is already in use by container "556f28922996...".
```

O serviço **já estava funcional** antes (`redis-cli ping` → `PONG`, `:6379` escutando). O `up` foi
tentado porque a task mandava; o conflito é o estado documentado em `AGENTS.md` §"O `docker compose
up` falha enquanto os containers do projeto `docker` antigo estiverem no ar". Migrar o namespace é
decisão do usuário (`docker rm postgres hive-redis hive-redis-insight`) e **não foi tomada aqui**.

---

## 5. Caveat honesto: a medição foi feita com working tree sujo

A árvore tinha modificações **pré-existentes de outros agentes** (orquestrador/documentação) no
momento da medição. Nenhum arquivo de código-fonte, teste ou migration estava sujo:

```
$ git status --porcelain | grep -E '\.(java|ts|vue|sql)$|pom\.xml$'
(sem resultado)
```

Único arquivo sujo sob `backend/` e `frontend/`:
`backend/bootstrap/src/main/resources/application-dev.properties` — que troca o datasource de
`localhost:5433` (postgres/postgres) para `localhost:5432` (dev_user/dev_password).

**Isso não afeta as contagens acima:** os ITs rodam no profile `test` com datasource injetado pelo
Testcontainers (`application-test.properties` não define datasource; `WebIntegrationTest` sobrescreve
via `@DynamicPropertySource`), e `application-dev.properties` só é carregado no profile `dev`. Os
testes unitários não abrem contexto Spring. Fica registrado porque **afetará `mvn spring-boot:run`
no profile `dev`**, que passará a apontar para o banco de outro projeto em `:5432` com credenciais
que o volume `docker_postgres_data` pode não ter. Não corrigido neste batch (regra do batch: não
alterar código).

---

## 6. Conclusão

**Baseline VERDE.** `371 / 75 / 189`, zero falhas, zero erros, zero skipped, nas três suítes,
medidos contra `e6d289e033efbeeb0fd1dbcd2fe6b9ffc4aabad8`.

- H2.2 (portão de entrada): **satisfeito** — não há débito pré-existente a sanear.
- P6 da revisão (re-medir, não adotar): **satisfeito** — baseline reescrito por medição.
- Pronto para BATCH 1 (extração D4/D5) sem bloqueio.

---

*Gerado pelo `developer-engineer` no BATCH 0 da feature `public-api-chat`. Nenhum arquivo de código,
teste ou migration foi alterado neste batch.*
