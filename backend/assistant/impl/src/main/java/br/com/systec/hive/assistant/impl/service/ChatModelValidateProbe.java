package br.com.systec.hive.assistant.impl.service;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class ChatModelValidateProbe implements ValidateProbe {

    @Override
    public String ping(ChatModel client) {
        return client.chat("ping");
    }
}
