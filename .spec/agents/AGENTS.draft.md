# AGENTS.md — rascunho (Hive)

> Rascunho preparado pelo orchestrator sob `.spec/`. **Não cole dentro dos
> marcadores `harness:v2:start/end` da raiz.** `AGENTS.md` é artefato ANEXADO do
> harness v2 com corpo gerado fixo (`AGENTS_BODY` em
> `scripts/harness-sync.mjs:254`): edição manual vira divergência WARN e pode ser
> sobrescrita por `/harness-update`. Para tornar isto efetivo, registre o arquivo
> em `opencode.json.instructions` (hoje só `.agents/rules/always-on.md`).

## Formato do repo

- Dois stacks independentes, sem workspace root: `backend/` (Maven) e
  `frontend/` (npm). Não existe `package.json` na raiz — `npm test` na raiz
  falha. `harness.config.json.commands.suiteFull` diz `npm test` e está errado
  para o root: suite real = `mvn test` em `backend/` + `npm test` em `frontend/`.
- Sem CI (`.github/workflows` ausente), sem hooks (`.githooks` ausente), sem
  lockfile no root. Único lockfile: `frontend/package-lock.json`.
- `harness.config.json.targets` está `null` para frontend e backend.

## Backend

- Agregador `backend/pom.xml`; módulos: dependencies, tenant, commons, bootstrap,
  identity, security, agent, provider, publicapi, integration-test.
- Cada domínio em quatro camadas: `api` (contratos/domínio), `impl` (negócio/JPA/
  integrações), `web` (REST versionado `internal/v1`), `dbupdate` (Flyway).
- Entrypoint executável: `backend/bootstrap/.../HiveApplication.java`.
  Rodar: `cd backend && mvn -pl bootstrap spring-boot:run`.
- Java 25 com `--enable-preview` obrigatório (compilador **e** surefire). JDK < 25
  não compila. Spring Boot real é 4.1.0 (`dependencies/pom.xml`), não 4.0.3 como
  afirma o badge do README — confie no pom.
- Versão CI-friendly `${revision}` = `1.0.0-SNAPSHOT` via flatten-maven-plugin.
- Unitários: surefire inclui `**/*Test*.java`. Convenção obrigatória de nome:
  `when_[acao]_with_[cenario]_then_[resultado]()`.
- Integração: módulo `integration-test`, `*IT.java` via failsafe + Testcontainers
  PostgreSQL (exige Docker); no agregador, o perfil `-Pintegration` desliga o
  surefire. `ChatSessionPromptTemplateMigrationIT` cobre guard/rollback da V15 do
  Flyway (V2..V15).
- `langchain4j.store.version` = `1.19.0-beta29` é o pin do **parser Tika**, não
  versão de store. Não existe LangChain4j store neste build: persistência
  vetorial é `KbVectorStore` + `KbEmbeddingStore` (JdbcTemplate). Não "corrigir"
  esse nome de propriedade.
- Segurança: PK UUIDv7, `tenantId` validado em toda camada, JWT + blacklist Redis,
  refresh rotation com fingerprint (`remoteIp`/`userAgent`).

## Frontend

- Vue 3 `<script setup>`, Pinia, PrimeVue 4, vue-router 5, Vite 8, TS 5.9,
  Vitest 3 (jsdom) + `@vue/test-utils`. Sem Tailwind/shadcn: o `DESIGN.md` da raiz
  é template do harness, não fonte visual deste projeto.
- `cd frontend && npm ci`; dev `npm run dev`; build `npm run build`
  (`vue-tsc -b && vite build`); teste `npm test` (`vitest run`).
- API base `/hive/v1` (`src/services/api.ts`); proxy do Vite `/hive` →
  `http://localhost:8080/hive`, sobreponha com `HIVE_API_PROXY`. Interceptor de
  401 faz logout + redirect `/login`.
- Testes colocated em `__tests__/`; `vitest.setup.ts` é o setup global.
- `.gitignore` ignora `.env*` (exceto `.env.example`) e `.spec/snapshots/` — nunca
  versionar credenciais nem dumps de banco.

## Harness / governança

- `orchestrator` escreve só em `.spec/**`. Protegidos: `.agents/**`,
  `opencode.json`, `harness.config.json`, `.github/workflows/**`, `.githooks/**`.
- `AGENTS.md` e `DESIGN.md` são ANEXADOS com corpo gerado — não editar à mão; use
  `/harness-update`.
- `doctor` local recusa: `target é a própria fonte: rode do checkout do harness`.
