package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.knowledge.api.domain.KbChunk;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The wiring, and the question the tutorial leaves open: does the official pipeline need an AI Service?
 *
 * <p>It does not. {@code DefaultRetrievalAugmentor.augment(AugmentationRequest)} is a plain method on
 * an interface — this project calls {@code ChatModel.chat(messages)} directly and has no AI Service
 * anywhere — and this test is the executable form of that answer: it builds the augmentor, runs the
 * whole chain and reads the injected message back, with nothing but the components.
 */
class RagAugmentorFactoryTest {

    private final RagAugmentorFactory factory = new RagAugmentorFactory();

    private static KbChunk chunk(String text) {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(UUID.randomUUID());
        chunk.setDocName("manual.pdf");
        chunk.setChunkId(UUID.randomUUID());
        chunk.setText(text);
        chunk.setScore(0.9);
        return chunk;
    }

    private static ContentRetriever returning(List<KbChunk> chunks, AtomicInteger calls) {
        return query -> {
            calls.incrementAndGet();
            List<Content> contents = new ArrayList<>();
            for (KbChunk chunk : chunks) {
                contents.add(RetrievedContent.of(chunk));
            }
            return contents;
        };
    }

    @Test
    void when_thePipelineRunsWithoutAnAiService_then_theContentsReachTheInjectedMessage() {
        AtomicInteger retrieved = new AtomicInteger();
        RetrievalAugmentor augmentor = factory.create(
                returning(List.of(chunk("primeiro"), chunk("segundo")), retrieved), null);
        UserMessage question = UserMessage.from("Qual o prazo?");

        AugmentationResult result = augmentor.augment(new AugmentationRequest(question,
                Metadata.from(question, UUID.randomUUID(), List.of())));

        assertThat(retrieved.get()).isEqualTo(1);
        assertThat(result.contents()).hasSize(2);
        assertThat(result.chatMessage())
                .as("our injector answers with a system message — see KnowledgeContextInjector")
                .isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) result.chatMessage()).text())
                .contains("primeiro")
                .contains("segundo")
                .contains("manual.pdf");
    }

    /** Every stage of the documented chain is really in the path, in the documented order. */
    @Test
    void when_thePipelineRuns_then_everyStageIsVisitedInOrder() {
        List<String> visited = new ArrayList<>();
        QueryTransformer transformer = query -> {
            visited.add("transformer");
            return List.of(query);
        };
        ContentRetriever retriever = query -> {
            visited.add("retriever");
            return List.of(RetrievedContent.of(chunk("trecho")));
        };
        UserMessage question = UserMessage.from("E o prazo?");

        factory.create(retriever, transformer).augment(new AugmentationRequest(question,
                Metadata.from(question, UUID.randomUUID(), List.of())));

        assertThat(visited).containsExactly("transformer", "retriever");
    }

    /**
     * A {@code null} transformer must fall through to the official no-op, not blow up: the stage is
     * optional and the pipeline has to run without it.
     */
    @Test
    void when_noTransformerIsGiven_then_theQueryPassesThroughUnchanged() {
        List<Query> seen = new ArrayList<>();
        ContentRetriever retriever = query -> {
            seen.add(query);
            return List.of();
        };
        UserMessage question = UserMessage.from("E o prazo?");

        AugmentationResult result = factory.create(retriever, null).augment(new AugmentationRequest(question,
                Metadata.from(question, UUID.randomUUID(), List.of())));

        assertThat(seen).singleElement().extracting(Query::text).isEqualTo("E o prazo?");
        assertThat(result.contents()).isEmpty();
    }

    /**
     * The library constraint behind the injection-target decision, pinned so a future upgrade that
     * relaxes it is visible: in 1.19.0 the augmentor only accepts a {@code UserMessage} and rejects
     * anything else. It is also why the context can only be re-targeted by a custom injector.
     */
    @Test
    void when_theAugmentedMessageIsNotAUserMessage_then_theLibraryRefusesIt() {
        RetrievalAugmentor augmentor = factory.create(
                returning(List.of(chunk("trecho")), new AtomicInteger()), null);
        SystemMessage notAQuestion = SystemMessage.from("Contexto");

        assertThatThrownBy(() -> augmentor.augment(new AugmentationRequest(notAQuestion,
                Metadata.from(notAQuestion, UUID.randomUUID(), List.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported message type");
    }

    /**
     * What the pipeline costs on its own, with the retrieval stubbed out.
     *
     * <p>The number the project cares about is the 57ms of the vector search, and this is the other
     * half: the orchestration between the question and the prompt. It is measured, not asserted by
     * feel, and the bound is deliberately loose — the assertion is here to catch a change that turns
     * the hot path into something structural (a pool, a lock, a second render of the template), not
     * to police microseconds on a shared CI machine.
     */
    @Test
    void when_thePipelineIsMeasuredWithAStubbedRetrieval_then_theOrchestrationIsNegligible() {
        int iterations = 2_000;
        RetrievalAugmentor augmentor = factory.create(
                returning(List.of(chunk("trecho de exemplo")), new AtomicInteger()), null);
        UserMessage question = UserMessage.from("Qual o prazo do contrato?");
        AugmentationRequest request = new AugmentationRequest(question,
                Metadata.from(question, UUID.randomUUID(), List.of()));

        for (int i = 0; i < 200; i++) {
            augmentor.augment(request);
        }
        long startedAt = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            augmentor.augment(request);
        }
        double microsPerCall = (System.nanoTime() - startedAt) / 1_000.0d / iterations;
        System.out.printf("rag pipeline orchestration: %.1f us/call over %d calls (retrieval stubbed)%n",
                microsPerCall, iterations);

        assertThat(microsPerCall)
                .as("orchestration budget: the retrieval itself is ~57000us, so anything structural here "
                        + "would show up immediately")
                .isLessThan(2_000d);
    }
}
