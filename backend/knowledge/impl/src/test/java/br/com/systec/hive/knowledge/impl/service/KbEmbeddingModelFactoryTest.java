package br.com.systec.hive.knowledge.impl.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The vendor router, as a table.
 *
 * <p>It used to be implied by the prefix table's prose, which listed {@code bge} among the families
 * "without prefix" — implying it was supported. It is not, on purpose: the family is served in 384,
 * 768 and 1024 dimensions and this project only stores 768 and 1536, so routing it to Ollama would
 * produce a base whose declared dimension can never match its vectors.
 */
class KbEmbeddingModelFactoryTest {

    @Test
    void when_localFamilies_then_routedToOllama() {
        for (String model : new String[]{"nomic-embed-text", "nomic-embed-text:v1.5", "minilm",
                "all-minilm-l6-v2", "mxbai-embed-large"}) {
            assertThat(KbEmbeddingModelFactory.isOllamaModel(model)).as(model).isTrue();
        }
    }

    @Test
    void when_remoteOrUnsupportedFamilies_then_routedToOpenAi() {
        for (String model : new String[]{"bge-base-en-v1.5", "bge-small-en", "bge-m3", "bge-large",
                "text-embedding-3-small", "cohere-embed-v3", "whatever-v9"}) {
            assertThat(KbEmbeddingModelFactory.isOllamaModel(model)).as(model).isFalse();
        }
        assertThat(KbEmbeddingModelFactory.isOllamaModel(null)).isFalse();
    }

    /**
     * The reason {@code bge} is not routed: the dimension table cannot name its size, so a base
     * declared with a {@code bge} model would claim a dimension the model never returns.
     */
    @Test
    void when_bgeIsDeclared_then_noLocalDimensionCanBePromised() {
        assertThat(KbEmbeddingService.dimensionOf("bge-base-en-v1.5"))
                .as("768 in reality, but nothing in the name says so: the honest answer is 'unknown'")
                .isEqualTo(1536);
        assertThat(KbEmbeddingService.dimensionOf("bge-small-en-v1.5")).isEqualTo(1536);
    }
}
