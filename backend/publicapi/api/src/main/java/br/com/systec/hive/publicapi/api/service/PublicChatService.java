package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;

public interface PublicChatService {
    Object complete(ChatCompletionRequestDTO request);
}