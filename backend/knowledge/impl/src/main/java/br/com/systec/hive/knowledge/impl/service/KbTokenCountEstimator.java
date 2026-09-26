package br.com.systec.hive.knowledge.impl.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * Counts <b>tokens</b>, so that "512" in {@link KbDocumentChunker} means the same thing the
 * embedding model will later mean by it.
 *
 * <h3>Why not {@code text.length() / 4}</h3>
 *
 * <p>The ratio behind that division is an average over English prose, and it is wrong by a large
 * factor in exactly the direction that hurts: Portuguese runs roughly 1.4&ndash;1.6 characters per
 * token against 4 for English, and a CJK run is close to one token per character &mdash; a
 * "2048 characters" budget that is really 512 tokens in English silently becomes 1500+ tokens of
 * Portuguese and 2000 tokens of Japanese. The budget is then not a budget at all: it is a number
 * that means something different for every document, and the chunk that comes out is sized by the
 * accident of which language the tenant happened to upload.
 *
 * <h3>Why not {@code OpenAiTokenCountEstimator}</h3>
 *
 * <p>Because it cannot be pointed at this project's models. Its
 * {@code OpenAiTokenCountEstimator(String modelName)} constructor resolves the name against
 * OpenAI's registry and throws when it is not there (verified in the 1.19.0 sources:
 * {@code getEncodingForModel(modelName).orElseThrow(unknownModelException())}), and the models
 * actually served here are {@code nomic-embed-text}, {@code mxbai}, {@code bge} and whatever a
 * tenant configures &mdash; none of which is in that registry. Borrowing its tokenizer by passing
 * some other model's name would work and would be a lie the next reader has to undo.
 *
 * <h3>What is counted, then</h3>
 *
 * <p>A real byte-pair encoding ({@code cl100k_base}) applied unconditionally, which is the same
 * thing LangChain4j itself falls back to when a model name is unknown. It is not the tokenizer of
 * the target model and is not claimed to be: it is a tokenizer, so the number is a token count
 * rather than a character count, and it is stable across languages. Chunk <i>size</i> is what this
 * governs, and every embedding model the project supports accepts these chunks without
 * truncation; which model is used for the vector is a separate decision already owned by
 * {@link EmbeddingTaskConvention}.
 *
 * <p>The encoding is immutable and shared; {@code countTokensOrdinary} holds no mutable state, so
 * one instance serves the concurrent ingestion of every tenant.
 */
public final class KbTokenCountEstimator implements TokenCountEstimator {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    private static final KbTokenCountEstimator INSTANCE = new KbTokenCountEstimator();

    private final Encoding encoding = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    /** The shared instance; the encoding is immutable, so there is nothing to configure per caller. */
    public static KbTokenCountEstimator getInstance() {
        return INSTANCE;
    }

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokensOrdinary(text);
    }

    /**
     * Only {@code estimateTokenCountInText} is on the chunking path &mdash;
     * {@code HierarchicalDocumentSplitter} sizes every segment with it and nothing else. The two
     * message methods exist because the interface declares them, so they count the same way rather
     * than by delegating to a character length.
     */
    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        if (message instanceof SystemMessage system) {
            return estimateTokenCountInText(system.text());
        }
        if (message instanceof AiMessage ai) {
            return estimateTokenCountInText(ai.text());
        }
        if (message instanceof UserMessage user) {
            StringBuilder sb = new StringBuilder();
            user.contents().stream()
                    .filter(TextContent.class::isInstance)
                    .map(TextContent.class::cast)
                    .forEach(content -> sb.append(content.text()).append('\n'));
            return estimateTokenCountInText(sb.toString());
        }
        return 0;
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (messages == null) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokenCountInMessage(message);
        }
        return total;
    }
}
