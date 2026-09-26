package br.com.systec.hive.commons.api.tools.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code DataIntegrityViolationException} → 409 sem vazar o schema do banco (OWASP A05).
 *
 * <p>É o único ponto do sistema que traduz uma mensagem do driver PostgreSQL em resposta HTTP. Essa
 * mensagem carrega o schema inteiro — nome da tabela, da coluna, da constraint e a definição dela
 * ({@code pg_get_constraintdef}). Se um dia alguém trocar um literal por {@code e.getMessage()}, o
 * banco inteiro vaza para qualquer cliente com um token válido.
 *
 * <p>Este teste existe porque a falha é silenciosa: a API continua respondendo 409 com um JSON
 * perfeito, só que com o dado que não deveria estar ali. Um IT não pegaria isso — precisaria
 * provocar cada constraint por um caminho específico, e o caminho genérico não tem nenhum.
 */
@DisplayName("ControllerExceptionHandler — integridade de dados")
class ControllerExceptionHandlerDataIntegrityTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();
    private final HttpServletRequest request = new MockHttpServletRequest("DELETE", "/hive/v1/agents/abc");

    // ============================================================ o caminho genérico

    @Test
    void when_unmappedConstraint_then_409WithActionablePtBrMessage() {
        // Uma constraint que o handler não conhece — a que sobrou depois que o CASCADE resolveu o
        // bug dos 409 de agente. A resposta ainda tem de ser útil em pt-BR, não "erro desconhecido".
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                violation("some_new_constraint", "fk_agent_thing", "agent_thing"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getDetail())
                .isNotBlank()
                .contains("Remova as dependências")
                .containsIgnoringCase("não foi possível");
    }

    @Test
    void when_unmappedConstraint_then_responseBodyLeaksNoSchemaInformation() {
        // A mensagem real do driver, verbatim. Nada disto pode aparecer no corpo.
        String driverMessage = "update or delete on table \"agent\" violates foreign key constraint "
                + "\"fk_chat_session_agent\" on table \"chat_session\"\n"
                + "  Key (id)=(867f0342-b6e8-42b9-8059-54468a20955c) is still referenced from table \"chat_session\".\n"
                + "  Hint: Referencer is defined as: FOREIGN KEY (agent_id) REFERENCES agent(id)";

        String body = serializedBody(handler.dataIntegrityViolation(violation(driverMessage), request));

        assertNoSchemaLeak(body);
    }

    // ============================================================ os ramos já mapeados também não vazam

    @ParameterizedTest(name = "constraint={0} → code={1}")
    @CsvSource({
            "fk_agent_prompt_template,PROMPT_IN_USE",
            "uniq_agent_default_per_tenant,CONCURRENT_DEFAULT_UPDATE",
            "uniq_prompt_active_code_per_tenant,PROMPT_CODE_DUPLICATE",
            "uk_users_email,EMAIL_DUPLICATE",
            "sole_default_agent,SOLE_DEFAULT_AGENT"
    })
    void when_mappedConstraint_then_409WithSpecificCodeAndNoSchemaLeak(String constraint, String expectedCode) {
        String driverMessage = "duplicate key value violates unique constraint \"" + constraint + "\"";
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(violation(driverMessage), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(expectedCode);
        assertNoSchemaLeak(serializedBody(response));
    }

    // ============================================================ chave duplicada não é dependência

    @ParameterizedTest(name = "constraint={0}")
    @CsvSource({
            "uq_kb_document_tenant_sha",
            "uk_provider_credential_tenant_provider",
            "uq_knowledge_base_tenant_slug"
    })
    void when_duplicateKey_then_409AlreadyExistsAndNotTheDependencyMessage(String constraint) {
        // A mensagem real do driver para violação de chave única. O default fala em "registro
        // referenciado por outros dados" / "remova as dependências", o que é factualmente falso
        // aqui: quem conflita é o próprio registro novo, e não existe dependência para remover.
        // Era esse o_branch que fazia o operador ficar sem ação possível.
        String driverMessage = "ERROR: duplicate key value violates unique constraint \""
                + constraint + "\"\n  Detail: Key (tenant_id, provider)=(...) already exists.";
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                new DataIntegrityViolationException("could not execute statement",
                        new IllegalStateException(driverMessage)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ALREADY_EXISTS");
        assertThat(response.getBody().getDetail())
                .contains("Já existe um registro")
                .doesNotContain("Remova as dependências")
                .doesNotContain("referenciado por outros dados");
        assertNoSchemaLeak(serializedBody(response));
    }

    @Test
    void when_uniqueConstraintWithoutTheDuplicateKeyPrefix_then_alsoAlreadyExists() {
        // A mesma constraint reportada só pela cláusula, sem o prefixo "duplicate key value".
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                violation("ERROR: violates unique constraint \"uk_provider_credential_tenant_provider\""),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    void when_unrelatedDatabaseError_then_keepsTheGenericDependencyMessage() {
        // O ramo de duplicata é estreito de propósito: um erro de banco que não é chave única nem
        // constraint nomeada continua caindo no default, em vez de virar "já existe um registro".
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                violation("ERROR: ON CONFLICT DO UPDATE command cannot affect row a second time"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getDetail()).contains("Remova as dependências");
    }

    @Test
    void when_specificUniqueConstraint_then_specificCodeWinsOverTheDuplicateBranch() {
        // O ramo de duplicata é o ÚLTIMO da cadeia: as constraints já nomeadas acima têm de continuar
        // vencendo, senão "prompt code já existe" viraria o genérico "já existe um registro".
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                violation("duplicate key value violates unique constraint \"uk_prompt_template_tenant_code_version\""),
                request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PROMPT_CODE_DUPLICATE");
    }

    // ============================================================ robustez

    @Test
    void when_causeIsNull_then_stillReturns409InsteadOfThrowing() {
        // getMostSpecificCause() devolve a própria exceção quando não há causa; o handler não pode
        // virar um 500 ao tentar traduzir o erro.
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                new DataIntegrityViolationException("boom"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isNotBlank();
        assertNoSchemaLeak(serializedBody(response));
    }

    @Test
    void when_violation_then_responseIsProblemJsonWithTheRequestUri() {
        ResponseEntity<StandardError> response = handler.dataIntegrityViolation(
                violation("fk_chat_session_agent", "chat_session", "agent"),
                request);

        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).isEqualTo("/hive/v1/agents/abc");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A mensagem do driver entra como causa raiz, que é de onde o handler a lê
     * ({@code getMostSpecificCause()} caminha até o fim da cadeia).
     *
     * <p>Por que o construtor de 2 argumentos e não um maior: {@code DataIntegrityViolationException}
     * tem <b>exatamente dois</b> construtores, {@code (String)} e {@code (String, Throwable)} —
     * conferido com {@code javap} em spring-tx 6.2.10. Uma chamada com 3 ou 4 argumentos é um erro
     * de aridade puro: não existe construtor com essa assinatura, e nada disso tem a ver com a
     * resolução de overload nem com a versão do JDK.
     *
     * <p>O que engana é o sintoma. Ao compilar esse erro de aridade aqui, o
     * {@code maven-compiler-plugin} 3.11.0 aborta com {@code Fatal error compiling:
     * CompilerException: ConcurrentModificationException} e <b>não imprime diagnóstico nenhum</b> —
     * a causa real fica invisível na saída do build, e o erro parece um problema de concorrência do
     * compilador. Os dois argumentos produzem a mesma cadeia de causas que um construtor maior
     * produziria, já que {@code getMostSpecificCause()} caminha até o fim dela.
     */
    private DataIntegrityViolationException violation(String driverMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new IllegalStateException(driverMessage));
    }

    private DataIntegrityViolationException violation(String constraint, String childTable, String parentTable) {
        return violation("update or delete on table \"" + childTable + "\" violates foreign key constraint \""
                + constraint + "\"\n  Key is still referenced from table \"" + parentTable + "\"");
    }

    /**
     * Serializa o {@link StandardError} com o mesmo Jackson que o usa em produção, para que o teste
     * veja o corpo como o cliente o vê.
     *
     * <p>Não é possível concatenar getters à mão aqui: {@code StandardError} tem <b>três</b> getters
     * para o mesmo campo — {@code getDetail}, {@code getDetailMessage} e {@code getMsg}. Os dois
     * últimos também são serializados e não apareceriam numa concatenação manual, ou seja, o teste
     * afirmava estar olhando o corpo inteiro enquanto olhava 6 dos 8 campos do JSON.
     */
    private String serializedBody(ResponseEntity<StandardError> response) {
        StandardError body = response.getBody();
        assertThat(body).isNotNull();
        try {
            return new ObjectMapper().writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new AssertionError("StandardError não serializou", e);
        }
    }

    /**
     * O corpo inteiro é verificado por identificadores de tabela/constraint e por sintaxe de DDL.
     * A palavra "agente" não entra: é vocabulário de domínio em pt-BR, não vazamento de schema.
     */
    private void assertNoSchemaLeak(String body) {
        assertThat(body).doesNotContain("agent_knowledge_base");
        assertThat(body).doesNotContain("chat_session");
        assertThat(body).doesNotContain("chat_message");
        assertThat(body).doesNotContain("prompt_template");
        assertThat(body).doesNotContain("FOREIGN KEY");
        assertThat(body).doesNotContain("REFERENCES");
        assertThat(body).doesNotContain("pg_get_constraintdef");
        assertThat(body).doesNotContain("pg_constraint");
        assertThat(body).doesNotContain("could not execute statement");
        assertThat(body).doesNotContain("duplicate key value");
        assertThat(body).doesNotContain("violates foreign key");
        assertThat(body).doesNotContain("is still referenced");
    }
}
