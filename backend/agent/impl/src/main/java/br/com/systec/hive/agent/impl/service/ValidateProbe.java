package br.com.systec.hive.agent.impl.service;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Seam for connection probing. Default impl hits the vendor; tests replace it.
 */
public interface ValidateProbe {

    String ping(ChatModel client);
}
