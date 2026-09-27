package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /v1/chat/sessions} com a sessão apontando para um prompt template.
 *
 * <p>Sucede {@code AgentDeleteCascadeIT}, que morreu com a entidade que ele testava. Duas das
 * garantias que ele carregava continuam vivas e é aqui que ficam travadas: o 409 de prompt em uso
 * e a uniformidade 404 (e não 403) para recurso de outro tenant. A exclusão em cascata de agente não
 * sobrevive porque não há mais agente — e o resto daquele arquivo era sobre a sessão, não sobre ele.
 */
class ChatSessionIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    // ============================================================ criação

    @Test
    void when_createSession_withPromptTemplate_then_201AndPersistsTheTemplate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-owner");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt de boas-vindas");

        String body = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promptTemplateId").value(promptTemplateId.toString()))
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", sessionId)).isEqualTo(1);
    }

    @Test
    void when_createSession_withoutPromptTemplate_then_400() throws Exception {
        // Não há prompt padrão e não há sessão sem prompt: a coluna é NOT NULL.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-sem-prompt");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Conversa\"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM chat_session")).isZero();
    }

    @Test
    void when_createSession_withUnknownPromptTemplate_then_404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-fantasma");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + UUID.randomUUID() + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_createSession_withPromptTemplateOfAnotherTenant_then_404AndNothingCreated() throws Exception {
        // Anti-IDOR: 404, não 403 — um 403 confirmaria que o registro existe.
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-atacante");
        String foreignToken = tokenFor(otherTenantId, "sess-dono-alheio");
        UUID foreignTemplate = createPromptTemplate(foreignToken, "Prompt alheio");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + foreignTemplate + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isNotFound());

        assertThat(count("SELECT count(*) FROM chat_session")).isZero();
    }

    // ============================================================ prompt em uso (o 409 que sobrevive)

    @Test
    void when_deletePromptTemplateInUseBySession_then_409WithPtBrMessageAndNoSchemaLeak() throws Exception {
        // O 409 NÃO vem só da FK: o serviço checa antes e lança PromptInUseException, e o
        // ON DELETE RESTRICT é a rede de segurança. O que se verifica aqui é que a recusa é
        // compreensível em pt-BR e que nada do schema vaza (OWASP A05).
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-em-uso");
        UUID promptTemplateId = createPromptTemplate(token, "Template Em Uso");
        createSession(token, promptTemplateId, "Conversa que segura o prompt");

        String body = mockMvc.perform(delete("/v1/prompt-templates/{id}", promptTemplateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMPT_IN_USE"))
                .andReturn().getResponse().getContentAsString();

        assertNoSchemaLeak(body);
        assertThat(body).as("corpo da resposta de 409").contains("sessão de chat");
        // Recusar apagar não pode derrubar a sessão nem o template.
        assertThat(count("SELECT count(*) FROM prompt_template WHERE id = ?", promptTemplateId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_session")).isEqualTo(1);
    }

    @Test
    void when_deleteUnusedPromptTemplate_then_204() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-livre");
        UUID promptTemplateId = createPromptTemplate(token, "Template Sem Sessão");

        mockMvc.perform(delete("/v1/prompt-templates/{id}", promptTemplateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM prompt_template WHERE id = ?", promptTemplateId)).isZero();
    }

    // ============================================================ o teto de paginação é do SERVIDOR

    @Test
    void when_listPromptsWithSizeAtTheCeiling_then_200AndOneAbove_then_400() throws Exception {
        // O `50` do seletor de prompt é o MESMO contrato em três lugares — o controller, o
        // componente e o teste do componente. O teste do componente prendia o componente à cópia
        // dele mesmo: se alguém baixasse o teto aqui para 20 sem tocar no frontend, o browser
        // levaria 400 em TODA montagem do seletor e o teste de frontend ficaria verde.
        //
        // Este IT é o que fecha o circuito: ele lê o número do SERVIDOR, então uma mudança no
        // controller quebra um teste em vez de quebrar a tela.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-teto-pagina");

        mockMvc.perform(get("/v1/prompt-templates?page=0&size=50").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/prompt-templates?page=0&size=51").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ a coluna agent_id foi mesmo embora

    @Test
    void when_migrationsApplied_then_agentIsGoneAndSessionPointsToTheTemplate() throws Exception {
        // Trava a decisão de schema deste batch, que é a parte irreversível do trabalho: nenhum
        // vestígio de `agent` pode reaparecer, e a sessão tem que apontar para o template desde o
        // DDL.
        //
        // O que mudou em 2026-09-27, quando o histórico de migrations do módulo `agent` foi
        // reescrito: `chat_session.agent_id_legacy` NÃO é criada. Ela existia na V15 para
        // preservar, de forma reversível, o agente ao qual cada sessão antiga apontava — mas sem a
        // tabela `agent` esse UUID não resolve para nada, e a aplicação nunca lia a coluna. O
        // histórico de dev foi descartado por decisão do usuário, então não há associação a
        // preservar. Esta é a ÚNICA diferença de schema entre a série anterior e a nova (a outra,
        // a queda de um índice parcial duplicado, é verificada em
        // `when_migrationsApplied_then_promptTemplateHasNoDuplicatedActiveIndex`).
        assertThat(columnExists("chat_session", "agent_id")).isFalse();
        assertThat(columnExists("chat_session", "agent_id_legacy")).isFalse();
        assertThat(tableExists("agent")).isFalse();
        assertThat(columnExists("chat_session", "prompt_template_id")).isTrue();
        assertThat(columnExists("tenant_llm_config", "provider")).isTrue();
    }

    @Test
    void when_migrationsApplied_then_promptTemplateIdIsNotNullByConstruction() throws Exception {
        // A invariante que substituiu o guard da V15. A V15 abortava o boot porque uma sessão
        // podia nascer sem template e o chat não teria para onde enviar a mensagem; com o
        // histórico reescrito, `prompt_template_id` é NOT NULL no próprio CREATE TABLE, e não um
        // SET NOT NULL conquistado depois de um backfill. O guard não encolheu — o schema torna o
        // caso impossível, e este teste trava isso.
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns"
                        + " WHERE table_name = 'chat_session' AND column_name = 'prompt_template_id'",
                String.class);
        assertThat(nullable).isEqualTo("NO");

        // E o dado tem que respeitar: não existe caminho de inserção que deixe a sessão órfã.
        String fkDeleteRule = jdbcTemplate.queryForObject(
                "SELECT rc.delete_rule FROM information_schema.referential_constraints rc"
                        + " JOIN information_schema.table_constraints tc"
                        + "   ON tc.constraint_name = rc.constraint_name"
                        + " WHERE tc.constraint_name = 'fk_chat_session_prompt_template'",
                String.class);
        assertThat(fkDeleteRule).isEqualTo("RESTRICT");
    }

    @Test
    void when_migrationsApplied_then_promptTemplateVersionCannotBeNullNorSubOne() throws Exception {
        // A 5a garantia deste arquivo, e a que fecha o defeito achado no code review de
        // 2026-09-27: `prompt_template.version` era nullable.
        //
        // Com ela, o UNIQUE (tenant_id, code, version) era INERTE nos NULLs — o PostgreSQL trata
        // NULL como distinto em UNIQUE (`NULLS DISTINCT` é o default), então duas linhas com o
        // mesmo code, mesmo tenant e version = NULL entravam as duas. E `ORDER BY version DESC` é
        // NULLS FIRST, então `findFirstByCodeAndTenantIdOrderByVersionDesc` devolvia a linha NULL
        // como "última versão" e `PromptTemplateServiceImpl:105` (`latest.getVersion() + 1`)
        // desempacotava Integer — NPE sem mensagem que dissesse a causa. O mesmo vale para
        // `PromptTemplateServiceImpl:82`, que lê a versão do banco e não da requisição.
        //
        // Hoje os 3 caminhos de escrita sempre gravam versão não-nula, então o serviço nunca chega
        // lá — mas o BANCO aceitava a linha, e basta o próximo batch abrir um caminho de escrita
        // (import, seed, migration de dado) para o NPE voltar sem deixar rastro.
        //
        // Verificado no banco, não por leitura: o NOT NULL e o CHECK abaixo estão no schema que o
        // Flyway aplicou.
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns"
                        + " WHERE table_name = 'prompt_template' AND column_name = 'version'",
                String.class);
        assertThat(nullable).as("version nullable torna o UNIQUE inerte e o ORDER BY DESC traz NULL primeiro")
                .isEqualTo("NO");

        // O CHECK fecha a outra ponta: 0 ou negativo ordenariam antes da v1 e voltariam a ser lidos
        // como "latest".
        String check = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                        + " WHERE conrelid = 'prompt_template'::regclass"
                        + "   AND conname = 'chk_prompt_template_version'",
                String.class);
        assertThat(check).isNotNull();
        assertThat(check).contains("version >= 1");

        // E o banco precisa recusar de fato, não só descrever. Vale por `version = NULL` e por
        // `version = 0`, que são as duas formas de quebrar a garantia.
        UUID tenantId = UUID.randomUUID();
        assertThat(bankAcceptsVersion(tenantId, "pt-null", null))
                .as("version = NULL tem de ser recusado pelo NOT NULL")
                .isFalse();
        assertThat(bankAcceptsVersion(tenantId, "pt-zero", 0))
                .as("version = 0 tem de ser recusado pelo CHECK")
                .isFalse();
        // E a via legítima continua passando, senão o teste só provaria que a tabela está fechada.
        assertThat(bankAcceptsVersion(tenantId, "pt-ok", 1))
                .as("version = 1 tem de ser aceito")
                .isTrue();
    }

    @Test
    void when_migrationsApplied_then_promptTemplateHasNoDuplicatedActiveIndex() throws Exception {
        // A série anterior tinha DOIS índices parciais UNIQUE idênticos sobre
        // (tenant_id, code) WHERE status = 'ACTIVE' — `uk_prompt_template_active_version`, criado
        // pela V3, e `uniq_prompt_active_code_per_tenant`, que a V7 criou de novo sem notar que o
        // primeiro já existia. Mesmo predicado, mesmas colunas: o segundo só custava uma escrita a
        // mais em toda linha de prompt_template. A série nova tem um.
        //
        // Este teste existe para o próximo não recriar o duplicado achando que é um índice novo.
        Integer duplicated = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public'"
                        + " AND indexdef LIKE '%UNIQUE%' AND indexdef LIKE '%ACTIVE%'",
                Integer.class);
        assertThat(duplicated).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private void assertNoSchemaLeak(String body) {
        assertThat(body).doesNotContain("chat_session")
                .doesNotContain("chat_message")
                .doesNotContain("prompt_template")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("REFERENCES")
                .doesNotContain("pg_constraint")
                .doesNotContain("key is still referenced")
                .doesNotContain("violates foreign key");
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createPromptTemplate(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"pt-" + UUID.randomUUID()
                                + "\",\"template\":\"Ola {{nome}}\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private void createSession(String token, UUID promptTemplateId, String title) throws Exception {
        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated());
    }

    /**
     * Grava um template com a versão indicada direto no banco e devolve {@code true} só se o banco
     * ACEITOU a linha.
     *
     * <p>Direto no JDBC, e não pelo endpoint, por uma razão específica: o serviço tem a própria
     * defesa (`if (version == null) setVersion(1)`), então um `POST /v1/prompt-templates` com
     * versão ausente NUNCA produziria NULL no banco — um teste pelo endpoint passaria sem provar
     * nada sobre a constraint. O que precisa ser provado é que o BANCO recusa, porque é o banco
     * que vai receber o dado do próximo caminho de escrita (import, seed, migration de dado).
     *
     * <p>Retorna {@code false} em vez de propagar a exceção: a rejeição é o resultado esperado,
     * não um erro do teste.
     */
    private boolean bankAcceptsVersion(UUID tenantId, String code, Integer version) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO prompt_template (tenant_id, code, name, template, version, status)"
                            + " VALUES (?, ?, 'probe', 'Ola', ?, 'DRAFT')",
                    tenantId, code, version);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private boolean columnExists(String table, String column) {        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = ? AND column_name = ?", Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
