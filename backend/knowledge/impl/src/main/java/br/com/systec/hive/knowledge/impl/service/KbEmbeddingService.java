package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;
import br.com.systec.hive.knowledge.api.port.KbEmbeddingPort;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies the model family's task-prefix convention and delegates the provider call to
 * {@link KbEmbeddingModelFactory}.
 *
 * <p>The document and the query are embedded through different methods because they are different
 * texts to the model — see {@link KbEmbeddingPort} for why that is not a naming preference.
 */
@Service
public class KbEmbeddingService implements KbEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(KbEmbeddingService.class);

    public static final String OPENAI_DEFAULT_MODEL = "text-embedding-3-small";
    public static final String OLLAMA_DEFAULT_MODEL = "nomic-embed-text";

    static final int MAX_CACHE_SIZE = 128;
    static final long ENTRY_TTL_MS = 30L * 60L * 1000L;
    private final KbEmbeddingModelFactory modelFactory;
    private final Map<String, CacheEntry> modelCache = new ConcurrentHashMap<>();

    public KbEmbeddingService(KbEmbeddingModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public float[] embedDocument(UUID tenantId, String model, int dimension, String text) {
        return embed(tenantId, model, dimension, text,
                EmbeddingTaskConvention.documentPrefix(normalizeModel(model)));
    }

    @Override
    public float[] embedQuery(UUID tenantId, String model, int dimension, String text) {
        return embed(tenantId, model, dimension, text,
                EmbeddingTaskConvention.queryPrefix(normalizeModel(model)));
    }

    private float[] embed(UUID tenantId, String model, int dimension, String text, String taskPrefix) {
        if (text == null) {
            // Sending a literal "null" to a paid endpoint is worse than failing here, and the
            // retrieval/ingestion callers both turn this into a reported failure.
            throw new IllegalArgumentException("text to embed is required");
        }
        if (EmbeddingTaskConvention.hasForeignRolePrefix(taskPrefix, text)) {
            // Reachable from user input: the question itself can start with a task prefix. The text
            // is still sent, but an operator looking at a weird score deserves to see why.
            log.warn("kb embedding text carries a task prefix of the other role model={}", model);
        }
        EmbeddingModel embeddingModel = resolveModel(tenantId, model);
        Embedding embedding = embeddingModel
                .embed(TextSegment.from(EmbeddingTaskConvention.apply(taskPrefix, text)))
                .content();
        float[] vector = embedding.vector();
        if (vector.length != dimension) {
            throw new KbInvalidUrlException("embedding dimension mismatch: expected " + dimension + " got " + vector.length);
        }
        return vector;
    }

    @Override
    public int dimensionFor(String model) {
        return dimensionOf(model);
    }

    /** Vector length of a model family, without needing an instance. */
    public static int dimensionOf(String model) {
        if (model == null) {
            return 1536;
        }
        String m = model.toLowerCase(Locale.ROOT);
        if (m.contains("nomic") || m.contains("768")) {
            return 768;
        }
        return 1536;
    }

    private static String normalizeModel(String model) {
        return model == null ? OPENAI_DEFAULT_MODEL : model;
    }

    private EmbeddingModel resolveModel(UUID tenantId, String model) {
        String normalizedModel = normalizeModel(model);
        String key = (tenantId == null ? "global" : tenantId.toString()) + ":" + normalizedModel;
        UUID capturedTenant = tenantId;
        String capturedModel = normalizedModel;
        evictStale();
        CacheEntry entry = modelCache.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                existing.touch();
                return existing;
            }
            return new CacheEntry(modelFactory.create(capturedTenant, capturedModel));
        });
        evictOverflow();
        return entry.model();
    }

    private void evictStale() {
        modelCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private void evictOverflow() {
        while (modelCache.size() > MAX_CACHE_SIZE) {
            String oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (Map.Entry<String, CacheEntry> e : modelCache.entrySet()) {
                long lastAccess = e.getValue().lastAccess();
                if (lastAccess < oldestAccess) {
                    oldestAccess = lastAccess;
                    oldest = e.getKey();
                }
            }
            if (oldest == null || modelCache.remove(oldest) == null) {
                break;
            }
        }
    }

    int cacheSize() {
        return modelCache.size();
    }

    static final class CacheEntry {
        private final EmbeddingModel model;
        private final long createdAt = System.currentTimeMillis();
        private final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());

        CacheEntry(EmbeddingModel model) {
            this.model = model;
        }

        EmbeddingModel model() {
            return model;
        }

        long lastAccess() {
            return lastAccess.get();
        }

        void touch() {
            lastAccess.set(System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ENTRY_TTL_MS;
        }
    }
}
