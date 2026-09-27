package br.com.systec.hive.agent.impl.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FailureClassifier")
class FailureClassifierTest {

    /**
     * The leak path that matters: an exception whose own {@code getMessage()} quotes what the user
     * typed. A driver or an application-layer wrapper can do that, and this classifier used to copy
     * the message verbatim into the log line.
     */
    @Test
    void when_exceptionMessageCarriesUserText_then_neitherReachesTheResult() {
        assertThat(FailureClassifier.causeOf(new IllegalStateException(
                "provider failed for query=[PERGUNTA SIGILOSA] using chunk=[salary of the CEO is 1M]")))
                .isEqualTo("IllegalStateException:unexpected");
    }

    @Test
    void when_causeOf_then_reportsClassAndClosedCodeWithoutQuotingTheMessage() {
        // timeout: the code must survive, the message must not
        assertThat(FailureClassifier.causeOf(new IllegalStateException("boom",
                new RuntimeException("ignored", new java.net.http.HttpTimeoutException("read timed out")))))
                .isEqualTo("HttpTimeoutException:timeout");
        // connection refused and 401 stay actionable without a message
        assertThat(FailureClassifier.causeOf(new java.net.ConnectException("Connection refused: host=10.0.0.1")))
                .isEqualTo("ConnectException:connection_refused");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.HttpException(401,
                "{\"error\":{\"message\":\"Incorrect API key provided: sk-live-PERGUNTA SIGILOSA\"}}")))
                .isEqualTo("HttpException:unauthorized");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.HttpException(429, "slow down")))
                .isEqualTo("HttpException:rate_limited");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.HttpException(503, "down")))
                .isEqualTo("HttpException:server_error");
        assertThat(FailureClassifier.causeOf(new java.sql.SQLException("relation \"agent\" does not exist")))
                .isEqualTo("SQLException:sql_error");
        // unknown shape: class only, never the message
        assertThat(FailureClassifier.causeOf(new IllegalArgumentException("valor invalido: PERGUNTA SIGILOSA")))
                .isEqualTo("IllegalArgumentException:unexpected");
        assertThat(FailureClassifier.causeOf(null)).isNull();
    }

    @Test
    void when_providerTypedException_then_classifiedFromItsOwnVocabulary() {
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.RateLimitException("429")))
                .isEqualTo("RateLimitException:rate_limited");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.AuthenticationException("401")))
                .isEqualTo("AuthenticationException:unauthorized");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.ModelNotFoundException("m")))
                .isEqualTo("ModelNotFoundException:model_not_found");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.InternalServerException("500")))
                .isEqualTo("InternalServerException:server_error");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.TimeoutException("t")))
                .isEqualTo("TimeoutException:timeout");
        assertThat(FailureClassifier.causeOf(new java.util.concurrent.CancellationException("c")))
                .isEqualTo("CancellationException:cancelled");
    }

    @Test
    void when_httpStatusIsNoneOfTheNamedOnes_then_reportsTheStatusItself() {
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.HttpException(418, "teapot")))
                .isEqualTo("HttpException:http_418");
        assertThat(FailureClassifier.causeOf(new dev.langchain4j.exception.HttpException(404, "not here")))
                .isEqualTo("HttpException:not_found");
    }

    @Test
    void when_causeChainSelfReferences_then_terminates() {
        assertThat(FailureClassifier.causeOf(new SelfCausedException()))
                .isEqualTo("SelfCausedException:unexpected");
    }

    @Test
    void when_causeChainFormsATwoNodeCycle_then_terminates() {
        // A <-> B: only a depth bound stops this, a self-reference check does not.
        MutualCause a = new MutualCause();
        MutualCause b = new MutualCause();
        a.other = b;
        b.other = a;

        assertThat(FailureClassifier.causeOf(a)).isEqualTo("MutualCause:unexpected");
    }

    /** A throwable that points at itself would loop forever without the cycle guard. */
    private static final class SelfCausedException extends RuntimeException {
        SelfCausedException() {
            super("self");
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    private static final class MutualCause extends RuntimeException {
        private MutualCause other;

        @Override
        public synchronized Throwable getCause() {
            return other;
        }
    }
}
