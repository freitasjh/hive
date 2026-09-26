package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.knowledge.impl.service.KbEmbeddingModelFactory;
import br.com.systec.hive.knowledge.impl.service.KbEmbeddingService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Substitutes only the <b>provider</b> edge, never the knowledge service.
 *
 * <p>The previous stubs replaced the whole {@code KbEmbeddingPort}, which meant the convention code
 * never ran in these tests: they were green no matter what the service sent to the model. Replacing
 * the model factory instead keeps the real {@link KbEmbeddingService} in the path — cache, role
 * selection, task prefix and all — and only the HTTP call to Ollama/OpenAI is faked.
 */
@TestConfiguration
public class StubEmbeddingProviderConfig {

    private static final List<String> SEEN_TEXTS = Collections.synchronizedList(new ArrayList<>());

    /** Every text the provider double was asked to embed, in order. */
    public static List<String> seenTexts() {
        synchronized (SEEN_TEXTS) {
            return List.copyOf(SEEN_TEXTS);
        }
    }

    public static void reset() {
        SEEN_TEXTS.clear();
    }

    @Bean
    @Primary
    KbEmbeddingModelFactory stubEmbeddingModelFactory() {
        KbEmbeddingModelFactory factory = Mockito.mock(KbEmbeddingModelFactory.class);
        Mockito.when(factory.create(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            String model = invocation.getArgument(1);
            String name = model == null ? KbEmbeddingService.OPENAI_DEFAULT_MODEL : model;
            return model(name);
        });
        return factory;
    }

    static EmbeddingModel model(String modelName) {
        return new TaskPrefixEmbeddingModel(modelName, KbEmbeddingService.dimensionOf(modelName), SEEN_TEXTS);
    }
}
