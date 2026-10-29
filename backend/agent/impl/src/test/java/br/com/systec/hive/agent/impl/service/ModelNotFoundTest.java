package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.util.ModelNotFound;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "the provider removed the model" rule, on its own.
 *
 * <p>These tests used to live in {@code LangChain4jTurnGatewayTest}, next to the class that owned the
 * rule as a private static method. They moved with it: the rule is now a statement about how providers
 * speak HTTP that two callers share, and the class that speaks HTTP is no longer its owner. The cases
 * are unchanged, including the null and the chain that wraps the model error twice.
 */
class ModelNotFoundTest {

    private static final String PROVIDER_JSON_404 =
            "{\"error\":{\"message\":\"The model `mimo-v2.5` does not exist or you do not have access to it.\","
                    + "\"type\":\"invalid_request_error\",\"code\":\"model_not_found\"}}";

    /** The opencode landing page: a 404 that means the baseUrl is missing its {@code /v1}. */
    private static final String HTML_404 =
            "<!DOCTYPE html><html><head><title>opencode.ai</title></head><body>not found</body></html>";

    @Test
    void when_theProviderAnswers404WithAModelError_then_itIsTheModelThatIsGone() {
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(404, PROVIDER_JSON_404))).isTrue();
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.ModelNotFoundException("model_not_found: mimo-v2.5"))).isTrue();
        assertThat(ModelNotFound.isModelNotFound(
                new RuntimeException("model_not_found"))).isTrue();
    }

    /**
     * A 404 carrying an HTML page is a route that does not exist, which is a configuration error. The
     * generic pattern is deliberately not applied to it: a landing page contains the words
     * "model ... not found" in its navigation, and matching that would turn a missing {@code /v1} into
     * a misleading "the model was removed".
     */
    @Test
    void when_theProviderAnswers404WithHtml_then_itIsTheRouteThatIsMissing() {
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(404, HTML_404))).isFalse();
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(404, "{\"error\":{\"message\":\"Not Found\"}}")))
                .as("a JSON 404 that does not mention the model is a route, not a model")
                .isFalse();
    }

    @Test
    void when_theFailureIsSomethingElse_then_itIsNotAModelProblem() {
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(500, "internal error"))).isFalse();
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(401, "invalid api key"))).isFalse();
        assertThat(ModelNotFound.isModelNotFound(
                new dev.langchain4j.exception.HttpException(403, "forbidden"))).isFalse();
        assertThat(ModelNotFound.isModelNotFound(new RuntimeException("LLM down"))).isFalse();
    }

    /** The chain is walked: a timeout arrives wrapped twice by the time the caller sees it. */
    @Test
    void when_theModelErrorIsWrapped_then_itIsStillFound() {
        RuntimeException wrapped = new RuntimeException("calling the provider",
                new IllegalStateException("transport",
                        new dev.langchain4j.exception.ModelNotFoundException("model_not_found")));

        assertThat(ModelNotFound.isModelNotFound(wrapped)).isTrue();
    }

    @Test
    void when_thereIsNoFailure_then_nothingIsAModelProblem() {
        assertThat(ModelNotFound.isModelNotFound(null)).isFalse();
    }
}
