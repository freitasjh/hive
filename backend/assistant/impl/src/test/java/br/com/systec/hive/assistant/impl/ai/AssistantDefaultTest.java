package br.com.systec.hive.assistant.impl.ai;

import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.impl.service.AbstractLangChain4jTurnGateway;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AI-02 / AC-01, AC-08: contrato congelado do ai service e precedência do prompt. */
class AssistantDefaultTest {

    private static final String SEEDED_TEXT = "Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, "
            + "de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe "
            + "em vez de inventar. Mantenha o tom profissional e cordial.";

    @Test
    void when_promptConstantIsRead_then_itMatchesTheV6SeedByteForByte() {
        assertThat(AssistantDefault.PROMPT).isEqualTo(SEEDED_TEXT);
    }

    @Test
    void when_chatMethodIsInspected_then_memoryIdIsSessionAndVariablesAreBound() throws Exception {
        Method chat = AssistantDefault.class.getMethod("chat", UUID.class, String.class, String.class);
        Parameter[] params = chat.getParameters();

        assertThat(chat.getReturnType()).isEqualTo(Result.class);
        assertThat(params[0].getType()).isEqualTo(UUID.class);
        assertThat(params[0].getAnnotation(MemoryId.class)).isNotNull();
        // @SystemMessage tem @Target({TYPE, METHOD}) no jar 1.19.0 — vai no método, não no parâmetro.
        assertThat(chat.getAnnotation(SystemMessage.class).value()).containsExactly("{{system}}");
        assertThat(params[1].getAnnotation(V.class).value()).isEqualTo("system");
        assertThat(chat.getAnnotation(UserMessage.class).value()).containsExactly("{{user}}");
        assertThat(params[2].getAnnotation(V.class).value()).isEqualTo("user");
    }

    @Test
    void when_sessionHasATemplate_then_itWinsOverTheFallback() {
        PromptTemplate template = new PromptTemplate();
        template.setTemplate("prompt escolhido pelo usuario");

        assertThat(lenientGateway().resolve(template)).isEqualTo("prompt escolhido pelo usuario");
    }

    @Test
    void when_sessionHasNoTemplate_then_theFallbackIsUsed() {
        PromptTemplate empty = new PromptTemplate();
        empty.setTemplate("   ");
        PromptTemplate absent = new PromptTemplate();

        assertThat(lenientGateway().resolve(empty)).isEqualTo(AssistantDefault.PROMPT);
        assertThat(lenientGateway().resolve(absent)).isEqualTo(AssistantDefault.PROMPT);
    }

    /** Exponha só a regra de precedência; o proxy nunca é chamado nestes testes. */
    private static ProbeGateway lenientGateway() {
        return new ProbeGateway();
    }

    private static final class ProbeGateway extends AbstractLangChain4jTurnGateway {
        private String resolve(PromptTemplate template) {
            return systemFor(template);
        }

        @Override
        public br.com.systec.hive.assistant.api.domain.TurnReply execute(
                br.com.systec.hive.assistant.api.domain.TurnRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected AssistantDefault proxyFor(ChatModel client) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected br.com.systec.hive.assistant.api.domain.TurnReply toReply(Result<String> r, int m, int c) {
            throw new UnsupportedOperationException();
        }
    }
}
