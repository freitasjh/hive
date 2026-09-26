package br.com.systec.hive.agent.impl.rag;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.concurrent.Executor;

/**
 * Builds the {@link RetrievalAugmentor} of a turn: the official pipeline the tutorial describes,
 * wired to this project's components.
 *
 * <pre>
 * UserMessage -> QueryTransformer -> QueryRouter -> ContentRetriever -> ContentAggregator -> ContentInjector
 * </pre>
 *
 * <p>Each stage is a collaborator the tutorial names, and none of them is reached through an AI
 * Service: {@code DefaultRetrievalAugmentor.augment(AugmentationRequest)} is a plain method, and this
 * project calls it directly before assembling the message list it hands to {@code ChatModel}. The
 * components that vary per turn — the retriever (tenant, bases, cut-off) and the transformer (it
 * carries this turn's conversation) — are parameters; the ones that do not (the injector) are built
 * once.
 */
public final class RagAugmentorFactory {

    /**
     * Runs work on the calling thread.
     *
     * <p>With one query and one retriever the augmentor never uses an executor at all — both branches
     * that do (several queries, several retrievers) are the ones this project does not take. The
     * decision not to leave it to the library is that its default is a lazily created unbounded
     * {@code ThreadPoolExecutor}: a per-turn object holding a lazily-created pool is a pool nobody
     * owns, and the alternative of passing the retrieval's own executor would put the retriever
     * behind a queue that the retriever itself fills and waits on. If a future routing change makes
     * the parallel branches reachable, this is the line that has to change with it.
     */
    private static final Executor SAME_THREAD = Runnable::run;

    private final KnowledgeContextInjector contentInjector;

    public RagAugmentorFactory() {
        this.contentInjector = KnowledgeContextInjector.create();
    }

    /**
     * @param retriever   this turn's retriever: tenant, linked bases, cut-off resolved per query
     * @param transformer this turn's transformer, or {@code null} for the official no-op
     *                    ({@code DefaultQueryTransformer}, which passes the query through)
     */
    public RetrievalAugmentor create(ContentRetriever retriever, QueryTransformer transformer) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .queryTransformer(transformer)
                .contentInjector(contentInjector)
                .executor(SAME_THREAD)
                .build();
    }
}
