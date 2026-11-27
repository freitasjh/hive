package br.com.systec.hive.ai.api;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.UUID;

/**
 * Streaming counterpart of {@link AssistantDefault} (ADR-001 D2): identical prompt contract,
 * {@link TokenStream} instead of {@code Result<String>}. Keep the two in lockstep — a divergent
 * template would answer a different question than the synchronous path.
 */
public interface AssistantStreamDefault {

    @SystemMessage("{{system}}")
    @UserMessage("{{user}}")
    TokenStream chat(@MemoryId UUID sessionId,
                     @V("system") String system,
                     @V("user") String user);
}
