package br.com.systec.hive.ai.api;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PX-02 — the streaming proxy carries the exact same prompt contract as the synchronous one.
 * Written before {@link AssistantStreamDefault} existed.
 */
class AssistantStreamDefaultParityTest {

    private static final Class<?>[] ARGS = {UUID.class, String.class, String.class};

    @Test
    void when_compareProxies_then_promptAnnotationsAndArgumentsAreIdentical() throws Exception {
        Method sync = AssistantDefault.class.getDeclaredMethod("chat", ARGS);
        Method stream = AssistantStreamDefault.class.getDeclaredMethod("chat", ARGS);

        assertThat(stream.getAnnotation(SystemMessage.class).value())
                .isEqualTo(sync.getAnnotation(SystemMessage.class).value());
        assertThat(stream.getAnnotation(UserMessage.class).value())
                .isEqualTo(sync.getAnnotation(UserMessage.class).value());

        for (int i = 0; i < ARGS.length; i++) {
            Annotation[] expected = sync.getParameterAnnotations()[i];
            Annotation[] actual = stream.getParameterAnnotations()[i];
            assertThat(Arrays.toString(actual)).as("parameter %d annotations", i).isEqualTo(Arrays.toString(expected));
        }
    }

    @Test
    void when_buildStreamingProxy_then_returnTypeIsTokenStream() throws Exception {
        Method stream = AssistantStreamDefault.class.getDeclaredMethod("chat", ARGS);

        assertThat(stream.getReturnType()).isEqualTo(TokenStream.class);
        assertThat(AssistantDefault.class.getDeclaredMethod("chat", ARGS).getReturnType()).isNotEqualTo(TokenStream.class);
    }

    @Test
    void when_buildWithStreamingChatModel_then_proxyIsCreated() {
        StreamingChatModel noop = new StreamingChatModel() { };

        AssistantStreamDefault proxy = AiServices.builder(AssistantStreamDefault.class)
                .streamingChatModel(noop)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(8).build())
                .build();

        assertThat(proxy).isNotNull();
        assertThat(MemoryId.class).isNotNull();
        assertThat(V.class).isNotNull();
    }
}
