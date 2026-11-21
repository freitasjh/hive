package br.com.systec.hive.ai.api.port;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;

/**
 * One turn of chat generation: prompt in, answer out.
 *
 * <p>The seam between "what the turn decided" and "how the answer was produced". The caller keeps
 * the transaction, the session, the history and the telemetry; the gateway keeps the prompt, the
 * provider call, the retry policy, the empty-reply guard and the model-not-found rule. Neither has
 * to grow to accommodate the other.
 *
 * <h3>What is on this side, and what is not</h3>
 *
 * <p>Diagnostics are deliberately <b>outside</b>. A gateway that emitted the trace, the RAG outcome
 * or the two log lines would be the only class in the chat path that could be asked why a turn
 * behaved as it did — and those lines are what found the six defects this code was validated against.
 * Splitting generation from observation is what keeps them both honest: the gateway cannot see a
 * session id to name, and the service cannot see a finish reason to report.
 *
 * <p>So the contract is total rather than exceptional: <b>a provider failure is returned, not
 * thrown</b> — see {@link TurnReply.Status#FAILED}. Not a stylistic choice. The caller's telemetry
 * needs the size of the prompt it sent, and that prompt is assembled here, so an exception thrown
 * from here would force the caller to reconstruct a number it never measured. The provider's own
 * exception travels back inside the reply, unwrapped and unmodified, so the caller still classifies
 * and logs exactly the object the transport produced.
 */
public interface ChatTurnGateway {

    /**
     * Generates one answer, or reports why it could not.
     *
     * @return what happened, in every case; never {@code null}
     */
    TurnReply execute(TurnRequest request);
}
