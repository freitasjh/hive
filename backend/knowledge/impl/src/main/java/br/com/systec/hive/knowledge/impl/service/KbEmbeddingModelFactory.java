package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.port.KbCredentialLookupPort;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds the langchain4j {@link EmbeddingModel} for a (tenant, model) pair — the outbound edge to
 * Ollama or OpenAI.
 *
 * <p>It exists as its own component so the vendor/credential knowledge stays out of
 * {@link KbEmbeddingService}: that service owns the vector cache and the task-prefix convention,
 * this one owns "which client talks to which vendor". A test can therefore substitute the provider
 * without substituting the convention, which is the only way an integration test can prove the
 * prefixes reach the wire.
 */
@Component
public class KbEmbeddingModelFactory {

    private static final Logger log = LoggerFactory.getLogger(KbEmbeddingModelFactory.class);

    public static final String OLLAMA_DEFAULT_BASE_URL = "http://localhost:11434";

    private final KbCredentialLookupPort credentialLookup;

    public KbEmbeddingModelFactory(KbCredentialLookupPort credentialLookup) {
        this.credentialLookup = credentialLookup;
    }

    public EmbeddingModel create(UUID tenantId, String model) {
        KbCredentialLookupPort.ResolvedKbCredential cred = credentialLookup.resolve(tenantId, model);
        if (isOllamaModel(model)) {
            String baseUrl = cred.baseUrl() != null ? cred.baseUrl() : OLLAMA_DEFAULT_BASE_URL;
            // The convention is on this line on purpose: when retrieval stops discriminating, the
            // first question is which space the stored vectors are in, and that is what this says.
            log.info("kb embedding model ollama model={} convention={}", model,
                    EmbeddingTaskConvention.describe(model));
            return OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
        }
        log.info("kb embedding model openai model={} convention={}", model,
                EmbeddingTaskConvention.describe(model));
        return OpenAiEmbeddingModel.builder()
                .apiKey(cred.apiKey())
                .modelName(model)
                .build();
    }

    /**
     * Whether {@code model} is served by a local Ollama rather than by OpenAI.
     *
     * <p>The list is the set of families this project can store end to end, and it is narrower than
     * "whatever Ollama happens to serve": a family is only routed here if
     * {@link KbEmbeddingService#dimensionOf} can name its dimension. {@code bge} is the case in
     * point — Ollama serves it, but in 384/768/1024 dimensions, and this project only has
     * {@code kb_embeddings_768} and {@code kb_embeddings_1536}. Routing it would create a base whose
     * declared dimension can never match the vector, failing at ingest with a mismatch; keeping it
     * out fails at configuration, where the message can be read. Adding a family here means adding
     * its dimension there first.
     */
    static boolean isOllamaModel(String model) {
        if (model == null) {
            return false;
        }
        String m = model.toLowerCase(Locale.ROOT);
        return m.contains("nomic") || m.contains("minilm") || m.contains("mxbai") || m.contains("all-minilm");
    }
}
