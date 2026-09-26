package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentDTO(
        UUID id,
        String name,
        String avatarUrl,
        String description,
        String systemPrompt,
        UUID promptTemplateId,
        String model,
        AiProvider provider,
        Double temperature,
        Double topP,
        Integer maxTokens,
        List<String> tools,
        List<String> knowledgeBases,
        boolean isDefault,
        boolean active,
        Instant createAt,
        Instant updateAt
) {
}
