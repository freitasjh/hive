# Database Migration Standards — Hive AI (Flyway)

> **Última revisão: 2026-09-27.** Esta regra já esteve desatualizada: ela ensinava um
> `AgentDbUpdateConfig` de exemplo **sem** `outOfOrder(true)` e **sem** `repair()`, que era o
> oposto do código real. Configuração ensinada errada é pior que configuração não ensinada, porque
> o próximo agente copia o exemplo e não confere no arquivo. A tabela de configuração real está
> abaixo e é a fonte da verdade — se o código divergir dela, corrija **esta** regra no mesmo batch.

## Module-Isolated Migrations

Each domain module has its **own Flyway configuration**, **own migration history table**, and **own migration location**. This allows independent schema evolution per bounded context.

| Module | Migration Location | History Table |
|--------|--------------------|---------------|
| identity | `classpath:/db/migration/identity_db` | `flyway_schema_history_identity` |
| tenant | `classpath:/db/migration/tenant_db` | `flyway_schema_history_tenant` |
| agent | `classpath:/db/migration/agent` | `flyway_schema_history_agent` |

## 🚨 A ARMADILHA DO `repair()` — LEIA ANTES DE REESCREVER QUALQUER MIGRATION

> **Esta seção é a razão de a regra existir.**

`flyway.repair()` realinha o **checksum** e a **descrição** das migrations **já aplicadas** com as
disponíveis no classpath, e marca como `deleted` as versions que sumiram do diretório. Ele **não
executa SQL**. Ele conserta o *histórico* para que o código novo "bata" com o que está gravado.

Consequência: **reescrever o histórico é invisível para o Flyway.** Medido com Flyway 10.10.0,
aplicando a série nova sobre um banco que tinha a série antiga, com colisão de número de versão
(nova V2 contra a V2 antiga gravada):

```
INFO: Repairing Schema History table for version 2 (Description: create prompt template, Checksum: -924871168)
>>> migrationsExecuted = 0
INFO: Successfully validated 12 migrations
```

O Flyway realinhou os checksums, **executou zero migrations** e reportou **sucesso**. O boot sobe
verde com o schema antigo. Sem o `repair()`, o mesmo cenário morre alto e verboso
(`Migration checksum mismatch for version 2`) — ou seja, **o `repair()` é o que transforma um erro
barulhento em mentira silenciosa.**

### Os módulos não são iguais entre si

| Módulo | `baselineOnMigrate` | `outOfOrder` | `repair()` | Reescrever o histórico sem `DROP SCHEMA` |
|---|---|---|---|---|
| `agent` | `true` | `true` | **sim** | 🔴 **0 migrations, sucesso reportado** |
| `identity` | `true` | — | **sim** | 🔴 **0 migrations, sucesso reportado** |
| `tenant` | `true` | — | **não** | 🟢 morre com `Migration checksum mismatch` |

Os dois primeiros **mentem**; o terceiro **grita**. Não generalizar "o Flyway pegaria" — depende do
módulo.

### Como reescrever com segurança

1. **DROP SCHEMA do módulo primeiro** (as tabelas dele + o `flyway_schema_history_{modulo}`), e
   deixe o `migrate()` do boot reaplicar do zero. É o caminho usado nos dois batches de 2026-09-27.
2. **Ou** versione as migrations novas **fora da faixa já aplicada** (ex.: a série antiga ia até
   V16, então comece em V20). Sem colisão de número, o checksum não tem o que realinar.
3. **Nunca** confie no `validate` como prova de que o schema está certo — ele valida o *histórico*,
   não o schema. A prova é `pg_dump --schema-only` comparado entre os dois lados.

> ⚠️ **`DROP SCHEMA` só é aceitável com o banco descartável.** Onde há dado a preservar, o caminho
> (2) é obrigatório. E o dump **antes**: `pg_dump` completo, não só `--schema-only`, porque o
> schema não é o que se perde.

## Configuration Pattern

Cada módulo define um `@Configuration` em `module/dbupdate/src/main/java/`. Este é o padrão **real**
de `agent`, o módulo mais completo:

```java
@Configuration
public class AgentDbUpdateConfig {

    @Bean(initMethod = "migrate")
    public Flyway agentFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/agent")
                .table("flyway_schema_history_agent")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load();
        // repair() realinha checksum das migrations aplicadas e NÃO executa SQL.
        // Reescrever o histórico exige DROP SCHEMA antes — ver a regra acima.
        flyway.repair();
        return flyway;
    }
}
```

O que cada flag faz, e por que está aqui:

- **`baselineOnMigrate(true)`** — cria a linha de baseline quando o schema já tem tabelas e o
  histórico está vazio. É o que permite adicionar um módulo novo a um banco existente. Em schema
  **vazio** não tem efeito: o Flyway simplesmente roda tudo.
- **`outOfOrder(true)`** (só `agent`) — aplica versions fora de ordem. Existe porque o histórico
  acumulou `V2..V10` e depois `V14`. Numa série reescrita e contígua, é desnecessário.
- **`flyway.repair()`** — o ponto perigoso. Ver a armadilha acima. Não remova sem ler aquela seção.

## 🚨 O JAR É O QUE SOBE, NÃO O `src/`

> **Erro medido em 2026-09-27, e ele produz um log que parece sucesso.**

O SQL das migrations vive em `src/main/resources`, mas `mvn spring-boot:run -pl bootstrap` resolve
os módulos irmãos do **repositório Maven**, não da árvore de fontes. Editar
`V2__create_prompt_template.sql` e subir a aplicação sem rebuild aplica o SQL **antigo**, e o log
diz:

```
INFO: Migrating schema "public" to version "2 - create prompt template"
INFO: Successfully applied 4 migrations to schema "public", now at version v5
```

Verde, "4 migrations aplicadas", schema errado. Aconteceu exatamente assim: a coluna ficou
nullable e o `CHECK` não existiu, apesar das duas linhas estarem no arquivo.

**Regra: depois de editar uma migration, `mvn clean install -DskipTests` ANTES de subir a
aplicação.** E, como o `repair()` mascara tudo, **confirme no banco** (`psql`), não no log:

```bash
unzip -p agent/dbupdate/target/hive-agent-dbupdate-*.jar \
  db/migration/agent/V2__create_prompt_template.sql | grep -n 'version.*INT'
```

## Naming Convention

- Pattern: `V{N}__description.sql`
- Versions start at V2 (V1 is baseline, pre-existing).
- Use double underscore `__` separator (Flyway convention).
- Description in snake_case: `V2__create_prompt_template.sql`, `V3__create_provider_credential.sql`
- Descrição muda = arquivo novo com version nova. **Nunca edite uma migration já aplicada** — em
  vez disso, reescreva a série inteira seguindo a seção de armadilha (é o que o projeto fez).

## SQL Conventions

- Use `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"` for UUID generation.
- Primary keys: `id UUID NOT NULL DEFAULT uuid_generate_v4()`.
- Timestamps: `create_at TIMESTAMP WITHOUT TIME ZONE`, `update_at TIMESTAMP WITHOUT TIME ZONE`.
- Multi-tenant tables declare `tenant_id UUID NOT NULL`.
- Constraints named explicitly: `CONSTRAINT pk_prompt_template PRIMARY KEY (id)`.
- Never use `ddl-auto=validate/create` — Flyway manages schema entirely.
- **Nunca escreva um delimitador dollar-quote (`$$`) num arquivo de migration.** O parser de script
  do Flyway corta o arquivo no delimitador, **mesmo dentro de um comentário** — o bloco é encerrado
  no meio e o arquivo passa a ser um SQL inválido. Se precisar de lógica condicional, resolva no
  DDL final; se não der, pense duas vezes se a migration precisa existir.

## 🔴 `UNIQUE` é inerte em NULL, e `ORDER BY DESC` traz NULL primeiro

> **Erro medido em 2026-09-27.** Achado pelo code review, na primeira versão da migration reescrita.

Uma coluna nullable que participa de um `UNIQUE` **não é protegida** por ele: o PostgreSQL trata
NULL como distinto (`NULLS DISTINCT` é o default). Duas linhas com o mesmo `code`, mesmo tenant e
`version = NULL` entram as duas, e a constraint passa a garantir nada:

```sql
INSERT INTO prompt_template (..., code, version) VALUES (..., 'dup', NULL);  -- INSERT 0 1
INSERT INTO prompt_template (..., code, version) VALUES (..., 'dup', NULL);  -- INSERT 0 1
```

O agravante: `ORDER BY version DESC` é **`NULLS FIRST`** no PostgreSQL, então a linha NULL volta
como se fosse a "última versão". Se alguém faz `latest.getVersion() + 1` com um `Integer`, é **NPE
sem mensagem que diga a causa** — e `PromptTemplateServiceImpl:82` e `:105` fazem exatamente isso.

**Regra: coluna que o código trata como número nunca pode ser nullable.** `version`, `position`,
`priority`, `sort_order`, qualquer contador — `NOT NULL DEFAULT` no `CREATE TABLE`, mais um
`CHECK` de faixa quando o valor tem piso ou teto. A garantia fica no schema, não no chamador.

Isto vale para **comentário** também: um comentário que afirma uma garantia que o código não
efetivamente faz é a pior forma de dívida, porque o próximo lê e confia. Ao escrever
`"a constraint X garante que Y"`, confirme que X garante Y **agora**, não no que você pretende.

## Divergência entre `@Column` da entidade e o DDL

Com `ddl-auto=none` (é o caso em `dev`, `prod` e `test`), **`length` e `columnDefinition` da
entidade são metadados inertes** — o Hibernate não gera e não valida schema, então uma divergência
entre a anotação e o DDL **não produz erro em runtime**. Ela produz mentira em código.

**Regra: o DDL é a fonte da verdade; a entidade deve refletir o DDL.** E quando a entidade estiver
**mais frouxa** que o DDL num campo de isolamento (`tenant_id`), tratar como dívida de segurança —
porque a entidade é a que mente.

Conhecidas em 2026-09-27, e reconciliadas onde foi seguro:

| # | Entidade | DDL | Resolução |
|---|---|---|---|
| 1 | `PromptTemplateEntity.version` — `nullable=false` | **corrigido** para `NOT NULL` + `CHECK` | DDL corrigido; agora as duas concordam |
| 2 | `ChatSessionEntity.summary` — `TEXT` | `VARCHAR(2000)` | débito: escolher um e alinhar |
| 3 | `ChatMessageEntity.role` — `length=20` | `VARCHAR(255)` | inofensivo (o `CHECK` é a regra real); alinhar por higiene |
| 4 | `PromptTemplateEntity.status` — sem `length` | `VARCHAR(20)` | **entidade corrigida** para `length = 20` |
| 5 | `AbstractEntity.tenantId` — `nullable=true` | **varia por tabela** | **não** reconciliado — ver abaixo |

### Por que `AbstractEntity.tenant_id` NÃO pode ser endurecido

Sete entidades em quatro módulos estendem `AbstractEntity` (`agent` ×4, `identity`, `tenant`,
`security`). O `nullable = true` do superclass está **errado para 6 delas e certo para 1**:

| Tabela | `tenant_id` no DDL | `AbstractEntity` diz |
|---|---|---|
| `chat_message`, `chat_session`, `prompt_template`, `provider_credential` | `NOT NULL` | `nullable = true` ❌ |
| `tenant_llm_config` | `NOT NULL` | `nullable = true` ❌ |
| **`users`** | **nullable** (`tenant_id UUID`, identity V2) | `nullable = true` ✅ |

Endurecer o superclass para `nullable = false` **trocaria uma mentira por outra**: agora
`UserEntity` afirmaria `NOT NULL` sobre uma coluna que o banco aceita como NULL, num campo de
isolamento de tenant. O defeito real não é a flag — é um `@MappedSuperclass` afirmar **um** contrato
de nulabilidade para uma coluna cujo contrato **legitimamente difere por contexto**.

A correção de fundo é por entidade (cada uma declarar o seu), não no compartilhado. Registrado como
débito em `.spec/tech-debts/`. Enquanto lá, o `nullable = true` é o único valor que não mente para
ninguém de forma catastrófica — o `NOT NULL` real está no DDL de 5 das 6 tabelas que o exigem, e o
isolamento é reforçado pelo `@Filter` de tenant, não por essa anotação.

## Adding a New Module Migration

1. Create `module/dbupdate/src/main/resources/db/migration/{module_name}/V{N}__description.sql`
2. Create `module/dbupdate/src/main/java/.../config/{Module}DbUpdateConfig.java`
3. Set unique history table name: `flyway_schema_history_{module}`
4. Add dbupdate module to parent `module/pom.xml`
5. Bootstrap `pom.xml` must depend on the new dbupdate module
6. Copy the config pattern above **inteiro** — incluindo `repair()` e o comentário dele.

## Rules

- **Never modify applied migrations** — create a new version instead. (A exceção sancionada pelo
  usuário em 2026-09-27 foi reescrever a série inteira do módulo `agent` com o banco descartável;
  isso exige `DROP SCHEMA` e dump antes — ver a armadilha do `repair()`.)
- **Always use `IF NOT EXISTS`** for extensions.
- **Always name constraints** explicitly (PK, FK, unique, check).
- **One migration per logical change** — don't batch unrelated schema changes.
- **Test migrations** against PostgreSQL 16** (not H2 or embedded DB).
- **Verifique no `psql`, não no log.** O log do Flyway mente quando o jar está velho e quando o
  `repair()` realinha.
