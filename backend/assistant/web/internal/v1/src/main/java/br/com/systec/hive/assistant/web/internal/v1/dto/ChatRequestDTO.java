package br.com.systec.hive.assistant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Um turno de chat. {@code provider}/{@code model} são um override OPCIONAL da conversa —
 * a UI envia o modelo escolhido no body (https://ui.shadcn.com/docs/ai-elements/base/model-selector,
 * consultado em 2026-11-20). Sem eles, o tenant continua usando o default de
 * {@code tenant_llm_config}; com {@code sessionId}, a escolha gravada na sessão é autoritativa.
 */
public record ChatRequestDTO(
        UUID promptTemplateId,
        UUID sessionId,
        @NotBlank @Size(max = 4000) String message,
        AiProvider provider,
        String model
) {
}
