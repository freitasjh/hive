package br.com.systec.hive.agent.impl.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

/**
 * Reading text out of the provider's message and response types.
 *
 * <p>These were three private static methods on one class — {@code textOf(ChatResponse)},
 * {@code textOf(ChatMessage)} and {@code promptChars(List)}. The duplication was not three copies of
 * a rule in three places; it was one class that had grown three readers of the same type, and none of
 * them was reachable from where the reading happens. The split became necessary when the RAG resolver
 * and the turn gateway each needed the block-size measurement, and at that point a rule stated once
 * next to the measurement was worth more than three correct copies that a library upgrade could make
 * disagree.
 *
 * <p>Nothing here logs or returns anything but a length where a length is asked for: the prompt
 * characters are telemetry, and prompt text must never reach a log line.
 */
public final class ProviderMessages {

    private ProviderMessages() {
    }

    /**
     * The text of a provider response, or {@code null} when there is none.
     *
     * <p>A 200 with no {@code aiMessage} and a 200 with an {@code aiMessage} whose text is
     * {@code null} are the same situation for the caller, and neither may reach a
     * {@code content NOT NULL} column.
     */
    public static String textOf(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        return response.aiMessage().text();
    }

    /** The text of a single message, or {@code null} for a type this project does not read. */
    public static String textOf(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof SystemMessage system) {
            return system.text();
        }
        if (message instanceof AiMessage ai) {
            return ai.text();
        }
        if (message instanceof UserMessage user) {
            return user.singleText();
        }
        return null;
    }

    /**
     * Size of an assembled prompt. Only the length is ever recorded — prompt and answer text must
     * not reach the log.
     */
    public static int totalChars(List<dev.langchain4j.data.message.ChatMessage> messages) {
        int total = 0;
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            String text = textOf(message);
            if (text != null) {
                total += text.length();
            }
        }
        return total;
    }
}
