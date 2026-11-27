package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import dev.langchain4j.model.chat.response.PartialThinking;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** F1/ND-13 against the real PostgreSQL schema: thinking is ephemeral, never a JPA row or column. */
class StreamingThinkingPersistenceIT extends WebIntegrationTest {

    private static final String TOKEN = "CANARIO-JPA-THINKING-8e42";

    @Test
    void when_thinkingStreamIsEmitted_then_noRowOrColumnCarriesTheToken() {
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(provider, handler -> {
                    handler.onPartialThinking(new PartialThinking(TOKEN));
                    handler.onPartialResponse("answer");
                    handler.onCompleteResponse(StreamingITFixtures.response("answer"));
                }), StreamingITFixtures.policy(),
                new StreamingMetrics(), StreamingITFixtures.scheduler())
                .stream(StreamingITFixtures.request(UUID.randomUUID()), event -> { });

        Long rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE content LIKE ?", Long.class, "%" + TOKEN + "%");
        Long tables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                        + "AND (table_name ILIKE '%stream%' OR table_name ILIKE '%thinking%')", Long.class);
        Long columns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND (column_name ILIKE '%stream%' OR column_name ILIKE '%think%')", Long.class);

        assertThat(rows).isZero();
        assertThat(tables).isZero();
        assertThat(columns).isZero();
    }
}
