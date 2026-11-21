package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;

import java.util.UUID;

public interface ChatService {
    ChatResponse chat(ChatRequest request);

    ChatResponse chat(ChatRequest request, UUID tenantId, UUID userId);
}
