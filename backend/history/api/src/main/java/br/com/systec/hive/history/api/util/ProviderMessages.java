package br.com.systec.hive.history.api.util;

import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.provider.api.ChatRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation in the provider's message type, dropping entries with no content.
 *
 * <p>System messages are kept in the list: a system prompt is an instruction to the model, and the
 * prompt builder places it ahead of the conversation.
 *
 * <p>Converted once per turn and handed to the gateway, which builds the prompt from it —
 * converting one history twice would be two chances to disagree about what was said.
 *
 * <h3>Why this is here and not in {@code impl}</h3>
 *
 * <p>It is a pure function of persisted state: it reads {@link ChatMessage} records and writes
 * provider messages, with no repository, no tenant and no Spring anywhere. The rule it carries —
 * which role maps to which provider message type, and that a message with no content is dropped —
 * is a statement about what a conversation <b>is</b>, and every caller of
 * {@code ChatTurnGateway} has to agree on it. Two callers that
 * each convert the history their own way is how the same conversation becomes two different prompts,
 * which is the divergence this project already paid for once.
 *
 * <h3>Not to be confused with the adapter's {@code ProviderMessages}</h3>
 *
 * <p>There is a class of the same name in the agent implementation module, and it is a
 * <b>different</b> function: that one reads text and sizes <em>out of</em> provider messages and
 * responses, and it belongs to the LangChain4j adapter that speaks the transport. This one converts
 * <em>into</em> them. The names collide on purpose — both are about provider messages — and the
 * split is the adapter/policy line: the reader stays with the adapter, the converter is policy.
 */
public final class ProviderMessages {

    private ProviderMessages() {
    }

    /**
     * The conversation as the provider's message type. Entries whose content is {@code null} on both
     * fields are dropped rather than sent as an empty message.
     */
    public static List<dev.langchain4j.data.message.ChatMessage> toProviderMessages(List<ChatMessage> history) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String content = msg.getContent() != null ? msg.getContent() : msg.getMessage();
            if (content == null) {
                continue;
            }
            if (msg.getRole() == ChatRole.ASSISTANT) {
                messages.add(new AiMessage(content));
            } else if (msg.getRole() == ChatRole.SYSTEM) {
                messages.add(new SystemMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }
}
