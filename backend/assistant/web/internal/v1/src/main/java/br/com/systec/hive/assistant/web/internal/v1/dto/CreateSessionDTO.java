package br.com.systec.hive.assistant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Criação de sessão. O par {@code provider}/{@code model} é opcional e, quando presente,
 * fixa o modelo DESTA conversa; ausente, a sessão usa o default do tenant em cada turno.
 */
public record CreateSessionDTO(
        @NotNull UUID promptTemplateId,
        @Size(max = 50) String title,
        AiProvider provider,
        String model
) {
}
