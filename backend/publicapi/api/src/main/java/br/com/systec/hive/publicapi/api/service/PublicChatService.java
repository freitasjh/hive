package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionResponse;

public interface PublicChatService {
    ChatCompletionResponse complete(ChatCompletionRequestDTO request);
}