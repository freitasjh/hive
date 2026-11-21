package br.com.systec.hive.assistant.api.util;

import java.util.regex.Pattern;

/**
 * Whether a provider failure means "the provider removed the model", as opposed to every other reason
 * a provider call can fail.
 *
 * <p>The whole chain is walked, because a timeout arrives wrapped twice by the time the caller sees
 * it. A {@code 404} is the interesting case and it is <b>not</b> enough on its own: only a JSON error
 * body that mentions the model counts. A {@code 404} carrying an HTML page is a route that does not
 * exist — a {@code baseUrl} without {@code /v1} — which is a configuration error and must not become a
 * misleading {@code 422 "model unavailable"}. The generic pattern is therefore not applied to an HTML
 * body: an opencode landing page contains the words "model ... not found" in its navigation and would
 * match.
 *
 * <h3>Why the rule lives in {@code api}</h3>
 *
 * <p>It is a statement about how providers speak HTTP — a {@code 404} means "the model was removed"
 * only when the body is a JSON error naming the model, and means "your {@code baseUrl} is missing the
 * {@code /v1}" when the body is an HTML landing page. The same status code carries two opposite
 * diagnoses, and the difference is in the transport's vocabulary.
 *
 * <p>That makes it policy, not adapter: the verdict decides a status code and an operator-facing
 * sentence, and it has to be the same verdict for every caller. It was a static method on
 * {@code LangChain4jTurnGateway} while there was exactly one caller, and the second caller — the
 * public chat API, which reuses the same port — would have had to either copy the rule or reach into
 * the gateway's internals for it. A rule with two homes is a rule that diverges, and the divergence
 * would be silent: both copies would be locally correct.
 *
 * <p>What is deliberately <b>not</b> here: the eviction of the cached vendor list, the log line and
 * the {@code 422}. Those name a session and a tenant, they are what an operator reads, and a rule
 * about HTTP status codes should not know that any of that exists.
 */
public final class ModelNotFound {

    /**
     * Wording a provider uses to say the model is gone. Deliberately narrow: it is applied to
     * exception messages and to provider error bodies, and a loose pattern here turns a routing
     * problem into a "model removed" verdict.
     */
    private static final Pattern MODEL_NOT_FOUND_PATTERN = Pattern.compile(
            "(?i)(model_not_found|no such model|unknown model|\\bmodel\\b.*(not found|does not exist))");

    private ModelNotFound() {
    }

    public static boolean isModelNotFound(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return true;
            }
            if (current instanceof dev.langchain4j.exception.HttpException http) {
                if (http.statusCode() == 404) {
                    if (isProviderModelErrorPayload(http.getMessage())) {
                        return true;
                    }
                    current = current.getCause();
                    continue;
                }
            }
            String message = current.getMessage();
            if (message != null && MODEL_NOT_FOUND_PATTERN.matcher(message).find()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * The body of the {@code HttpException} is the provider's raw payload. Only a JSON error that
     * names the model indicates a removed model; anything else (HTML, empty, a JSON about the route)
     * is an endpoint configuration failure.
     */
    private static boolean isProviderModelErrorPayload(String body) {
        if (body == null) {
            return false;
        }
        String payload = body.stripLeading();
        if (!payload.startsWith("{")) {
            return false;
        }
        return MODEL_NOT_FOUND_PATTERN.matcher(payload).find();
    }
}
