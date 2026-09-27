package br.com.systec.hive.integrationtest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code agent/V15__chat_session_prompt_template.sql} contra um banco no estado V14.
 *
 * <p>Esta é a única barreira entre um banco com sessão órfã e um {@code NULL} silencioso, e ela não
 * tem teste de outro jeito: a aplicação sobe com o schema já migrado, então um banco de teste com
 * histórico legado não acontece em nenhum outro IT. Por isso o container aqui é dedicado e as
 * migrations são aplicadas por etapas — V2..V14, depois V15 — com o Flyway como biblioteca, que é
 * exatamente o que o boot faz.
 *
 * <p>NÃO estende {@code WebIntegrationTest}: este teste não sobe a aplicação, só o PostgreSQL e o
 * Flyway. A application context seria um custo sem nenhuma informação a mais.
 *
 * <p>O que fica travado:
 * <ul>
 *   <li>a migration ABORTA quando há sessão cujo agente não tinha template;</li>
 *   <li>o rollback é INTEIRO — a coluna do passo 1 não existe depois, e o histórico Flyway não
 *       gravou a V15. É o que a transactionalidade do Flyway em PostgreSQL garante, e o que
 *       sustenta a decisão de abortar em vez de avisar;</li>
 *   <li>a mensagem é AUTO-SSUFICIENTE: recomeça pelo {@code ADD COLUMN} (porque o rollback a
 *       apagou), lista os tenants atingidos, e separa quem tem template de quem não tem — porque
 *       o mesmo UPDATE resolve um grupo e não resolve o outro.</li>
 * </ul>
 */
class ChatSessionPromptTemplateMigrationIT {

    private static final String MIGRATIONS = "classpath:/db/migration/agent";
    private static final String HISTORY = "flyway_schema_history_agent";

    private static final PostgreSQLContainer<?> PG;
    private static final String MAINTENANCE_DB = "postgres";

    static {
        PG = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));
        PG.start();
    }

    /**
     * A URL é montada à mão em vez de {@code getJdbcUrl(db)}: o Testcontainers deste projeto
     * (1.20.4) só oferece a sobrecarga sem argumento, e um teste que depende de API de biblioteca
     * para Versionamento quebra na próxima atualização sem que nada tenha mudado aqui.
     */
    private static String urlFor(String database) {
        return "jdbc:postgresql://" + PG.getHost() + ":" + PG.getMappedPort(5432) + "/" + database;
    }

    /**
     * Cria um banco novo a partir do de manutenção. Um container por teste seria mais simples, mas
     * quatro containers para quatro testes é startup_customer(); o isolamento é o mesmo e o custo
     * é uma vez só.
     */
    private static void freshDatabase(String name) throws Exception {
        try (Connection c = DriverManager.getConnection(urlFor(MAINTENANCE_DB), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
    }

    // ============================================================ o guard

    @Test
    void when_anAgentHasNoPromptTemplate_then_migrationAbortsAndLeavesTheSchemaUntouched() throws Exception {
        freshDatabase("guard");
        migrateTo("guard", 14);
        seedTenantWithOrphanSession("guard", true);

        assertThatThrownBy(() -> migrateTo("guard", 15))
                .hasMessageContaining("V15 ABORTADA")
                .hasMessageContaining("NADA FOI ALTERADO");

        // (b) schema intacto: o rollback desfez também o ADD COLUMN do passo 1. Se a coluna
        // sobrevivesse, o próximo start tentaria o ADD de novo — que tem IF NOT EXISTS, então
        // passaria — e a V15 "rodaria" sobre um schema que ninguém planejou.
        assertThat(columnExists("guard", "chat_session", "prompt_template_id"))
                .as("prompt_template_id não pode sobrar de uma migration que abortou")
                .isFalse();
        assertThat(columnExists("guard", "chat_session", "agent_id"))
                .as("agent_id precisa continuar como estava: a V15 não pode ter renomeado nada")
                .isTrue();
        assertThat(tableExists("guard", "agent"))
                .as("a V16 não pode ter rodado; a V15 abortou antes dela")
                .isTrue();

        // (b) o histórico não gravou a V15. Uma linha de falha aqui diria que o próximo boot
        // tentaria de novo — o que é verdade e desejado — mas também que o operador precisa
        // intervir, e ele só descobre isso se a tentativa falhar.
        assertThat(flywayVersion("guard", "15")).isZero();
        assertThat(columnCount("guard", "chat_session")).isEqualTo(columnCountBefore("guard"));
    }

    @Test
    void when_theGuardFires_then_theMessageIsSelfSufficient() throws Exception {
        freshDatabase("msg");
        migrateTo("msg", 14);
        UUID comTemplate = seedTenantWithOrphanSession("msg", true);
        UUID semTemplate = seedTenantWithOrphanSession("msg", false);

        String message = null;
        try {
            migrateTo("msg", 15);
        } catch (RuntimeException e) {
            message = rootMessage(e);
        }
        assertThat(message).as("a migration deveria ter abortado").isNotNull();

        // 1. Recomeça pelo ADD COLUMN, porque o rollback o desfez. Sem isso o operador copia o
        //    UPDATE do passo 2 e leva "column does not exist" — a falha que a review reproduziu.
        assertThat(message)
                .contains("ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS prompt_template_id UUID")
                .contains("O ROLLBACK TAMBEM DESFEZ O PASSO 1 DESTA MIGRATION");

        // 2. Lista os tenants atingidos, não só a contagem: com 40 órfãs em 6 tenants, a contagem
        //    obrigaria o operador a escrever query própria para descobrir quem é quem.
        assertThat(message)
                .contains(comTemplate.toString())
                .contains(semTemplate.toString());

        // 3. Diz o que fazer quando o tenant NÃO tem template, e diz que o UPDATE não resolve.
        assertThat(message)
                .contains("NAO TEM NENHUM prompt template")
                .contains("O UPDATE do passo 2 NAO resolve estes tenants")
                .contains("Nao ha atalho para este caso")
                .contains("Tenants COM template:")
                .contains("Tenants SEM template:");

        // 4. "Rode a migration de novo" não é solução sozinha — a mensagem deixa isso explícito.
        assertThat(message)
                .contains("So depois dos passos 2 e 3 a V15 roda sozinha")
                .contains("Rodar a migration antes disso falha com esta mesma mensagem");
    }

    /**
     * O passo que faltava na review: não basta a mensagem CONTER um SQL, ela precisa ser
     * <b>executável</b>. Uma mensagem de remédio que não roda é pior do que nenhuma — dá
     * confiança de que existe caminho.
     *
     * <p>Este teste executa os três passos exatamente como a mensagem os entrega (só troca
     * {@code <TENANT_ID>} pelo id do tenant), e afirma que a V15 passa a rodar sozinha. Se alguém
     * trocar uma coluna, tirar o {@code update_at} ou errar uma aspa na mensagem, este teste falha
     * e não o operador.
     */
    @Test
    void when_theOperatorFollowsTheMessage_then_theSqlRunsAndTheMigrationCompletes() throws Exception {
        freshDatabase("receita");
        migrateTo("receita", 14);
        UUID comTemplate = seedTenantWithOrphanSession("receita", true);
        UUID semTemplate = seedTenantWithOrphanSession("receita", false);

        String message = null;
        try {
            migrateTo("receita", 15);
        } catch (RuntimeException e) {
            message = rootMessage(e);
        }
        assertThat(message).isNotNull();

        // Passo 1, colado da mensagem.
        execute("receita", sqlBlock(message, "ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS"));
        assertThat(columnExists("receita", "chat_session", "prompt_template_id")).isTrue();

        // Passo 2, colado da mensagem. Deixa para trás exatamente a órfã do tenant sem template —
        // o comportamento que a mensagem promete, e a razão do passo 3 existir.
        execute("receita", sqlBlock(message, "UPDATE chat_session"));
        assertThat(scalar("receita", "SELECT count(*) FROM chat_session WHERE prompt_template_id IS NULL"))
                .as("o UPDATE do passo 2 não pode resolver o tenant que não tem template")
                .isEqualTo(1);

        // Passo 3, colado da mensagem, com o placeholder trocado pelo tenant que a lista nomeia.
        String insert = sqlBlock(message, "INSERT INTO prompt_template")
                .replace("<TENANT_ID>", semTemplate.toString());
        assertThat(insert).contains("'" + semTemplate + "'");
        execute("receita", insert);
        assertThat(scalar("receita",
                "SELECT count(*) FROM prompt_template WHERE tenant_id = '" + semTemplate + "'"))
                .isEqualTo(1);

        // Passo 2 de novo, agora que o tenant sem template tem um.
        execute("receita", sqlBlock(message, "UPDATE chat_session"));
        assertThat(scalar("receita", "SELECT count(*) FROM chat_session WHERE prompt_template_id IS NULL"))
                .isZero();

        // Passo 4: a V15 roda sozinha, e os dois tenants têm sessão apontando para template real.
        migrateTo("receita", 15);
        assertThat(columnExists("receita", "chat_session", "agent_id_legacy")).isTrue();
        assertThat(columnExists("receita", "chat_session", "agent_id")).isFalse();
        assertThat(flywayVersion("receita", "15")).isEqualTo(1);
        assertThat(scalar("receita",
                "SELECT count(*) FROM chat_session cs JOIN prompt_template pt ON pt.id = cs.prompt_template_id"
                        + " WHERE cs.tenant_id IN ('" + comTemplate + "', '" + semTemplate + "')"))
                .as("toda sessão dos dois tenants aponta para um template do próprio tenant")
                .isEqualTo(2);
    }

    // ============================================================ o caminho do sucesso

    @Test
    void when_everyAgentHasAPromptTemplate_then_migrationBackfillsAndRenames() throws Exception {
        freshDatabase("ok");
        migrateTo("ok", 14);
        UUID templateId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (Connection c = connect("ok"); Statement s = c.createStatement()) {
            s.execute("INSERT INTO prompt_template (id, tenant_id, code, name, template, version, status) VALUES ('"
                    + templateId + "', '" + tenantId + "', 'p1', 'P1', 'texto', 1, 'ACTIVE')");
            s.execute("INSERT INTO agent (id, tenant_id, name, llm_model, provider_type, active, is_default) VALUES ('"
                    + agentId + "', '" + tenantId + "', 'A', 'gpt-4o-mini', 'OPENAI', true, false)");
            s.execute("UPDATE agent SET prompt_template_id = '" + templateId + "' WHERE id = '" + agentId + "'");
            s.execute("INSERT INTO chat_session (id, tenant_id, user_id, agent_id, title) VALUES ('"
                    + sessionId + "', '" + tenantId + "', '" + UUID.randomUUID() + "', '" + agentId + "', 'S')");
        }

        migrateTo("ok", 15);

        assertThat(columnExists("ok", "chat_session", "prompt_template_id")).isTrue();
        assertThat(columnExists("ok", "chat_session", "agent_id")).isFalse();
        assertThat(columnExists("ok", "chat_session", "agent_id_legacy"))
                .as("a coluna antiga é RENOMEADA, não apagada: a reversão depende disso")
                .isTrue();
        assertThat(columnNotNull("ok", "chat_session", "prompt_template_id")).isTrue();
        assertThat(flywayVersion("ok", "15")).isEqualTo(1);

        // O backfill preservou a associação por DADO: a sessão aponta para o mesmo template que o
        // agente apontava. É esta asserção que separa "a coluna existe" de "o dado sobreviveu".
        try (Connection c = connect("ok");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT prompt_template_id FROM chat_session WHERE id = '" + sessionId + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getObject(1).toString()).isEqualTo(templateId.toString());
        }
    }

    @Test
    void when_thereAreNoChatSessionsAtAll_then_theGuardDoesNotFire() throws Exception {
        // O guard não pode travar uma instalação nova. Um banco recém-criado tem zero sessão, e o
        // único estado que o faz rodar é "nenhuma órfã" — inclusive quando `agent` existe e está
        // vazio, que é o estado de um ambiente novo depois da V14.
        freshDatabase("vazio");
        migrateTo("vazio", 14);

        assertThat(tableExists("vazio", "chat_session")).isTrue();

        migrateTo("vazio", 15);

        assertThat(columnExists("vazio", "chat_session", "prompt_template_id")).isTrue();
        assertThat(columnExists("vazio", "chat_session", "agent_id")).isFalse();
        assertThat(columnExists("vazio", "chat_session", "agent_id_legacy")).isTrue();
        assertThat(flywayVersion("vazio", "15")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Aplica as migrations do módulo `agent` até a versão {@code target}, inclusive. É o mesmo
     * caminho de código do boot: a localização, a history table e a estratégia são as mesmas.
     */
    private static void migrateTo(String database, int target) {
        Flyway flyway = Flyway.configure()
                .dataSource(urlFor(database), PG.getUsername(), PG.getPassword())
                .locations(MIGRATIONS)
                .table(HISTORY)
                .baselineOnMigrate(true)
                .target(String.valueOf(target))
                .load();
        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted == 0 && !result.success) {
            throw new IllegalStateException("migrate() nao executou nada e nao teve sucesso");
        }
    }

    private static Connection connect(String database) throws Exception {
        return DriverManager.getConnection(urlFor(database), PG.getUsername(), PG.getPassword());
    }

    /**
     * Cria um tenant e uma sessão cujo agente aponta o template para {@code null} (a sessão fica
     * órfã) ou para um template novo (a sessão é backfilled).
     *
     * @return o id do tenant criado
     */
    private static UUID seedTenantWithOrphanSession(String database, boolean comTemplate) throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        try (Connection c = connect(database); Statement s = c.createStatement()) {
            if (comTemplate) {
                s.execute("INSERT INTO prompt_template (id, tenant_id, code, name, template, version, status) VALUES ('"
                        + UUID.randomUUID() + "', '" + tenantId + "', 'p1', 'P1', 'texto', 1, 'ACTIVE')");
            }
            s.execute("INSERT INTO agent (id, tenant_id, name, llm_model, provider_type, active, is_default) VALUES ('"
                    + agentId + "', '" + tenantId + "', 'A', 'gpt-4o-mini', 'OPENAI', true, false)");
            s.execute("INSERT INTO chat_session (id, tenant_id, user_id, agent_id, title) VALUES ('"
                    + UUID.randomUUID() + "', '" + tenantId + "', '" + UUID.randomUUID() + "', '" + agentId + "', 'S')");
        }
        return tenantId;
    }

    /**
     * Recorta da mensagem o comando que começa em {@code firstLine} e vai até o ponto-e-vírgula
     * que o fecha, devolvendo-o sem indentação — exatamente como o operador o copiaria.
     *
     * <p>Recortar e não reescrever é o ponto: um teste que montasse o próprio SQL provaria que o
     * teste sabe escrever SQL, não que a mensagem entrega algo executável.
     */
    private static String sqlBlock(String message, String firstLine) {
        String[] lines = message.split("\\R");
        StringBuilder block = new StringBuilder();
        boolean started = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (!started) {
                if (!line.startsWith(firstLine)) {
                    continue;
                }
                started = true;
            }
            block.append(line).append('\n');
            if (line.endsWith(";")) {
                return block.toString();
            }
        }
        throw new AssertionError("a mensagem nao contem um comando que comecasse com: " + firstLine);
    }

    private static void execute(String db, String sql) throws Exception {
        try (Connection c = connect(db); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static boolean columnExists(String db, String table, String column) throws Exception {
        return scalar(db, "SELECT count(*) FROM information_schema.columns WHERE table_name = '"
                + table + "' AND column_name = '" + column + "'") > 0;
    }

    private static boolean columnNotNull(String db, String table, String column) throws Exception {
        return "NO".equals(scalarText(db, "SELECT is_nullable FROM information_schema.columns WHERE table_name = '"
                + table + "' AND column_name = '" + column + "'"));
    }

    private static boolean tableExists(String db, String table) throws Exception {
        return "t".equals(scalarText(db, "SELECT coalesce(to_regclass('public." + table + "')::text, 'f') <> 'f'"));
    }

    /** Quantas linhas a migration gravou na history para a versão pedida. */
    private static int flywayVersion(String db, String version) throws Exception {
        return scalar(db, "SELECT count(*) FROM " + HISTORY + " WHERE version = '" + version + "' AND success");
    }

    private static int columnCount(String db, String table) throws Exception {
        return scalar(db, "SELECT count(*) FROM information_schema.columns WHERE table_name = '" + table + "'");
    }

    private static int columnCountBefore(String db) throws Exception {
        return V14_COLUMN_COUNT;
    }

    /**
     * Contagem de colunas de {@code chat_session} depois da V14. Constante, e não lida do banco de
     * propósito: o teste precisa afirmar que o rollback não mudou NADA, e comparar com o estado
     * que ele mesmo acabou de observar não provaria nada.
     */
    private static final int V14_COLUMN_COUNT = 8;

    private static int scalar(String db, String sql) throws Exception {
        try (Connection c = connect(db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String scalarText(String db, String sql) throws Exception {
        try (Connection c = connect(db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * A mensagem do PostgreSQL chega embrulhada em {@code PSQLException} dentro do
     * {@code FlywayMigrateException}; o que importa aqui é o texto que o operador veria no log.
     */
    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
