package br.com.systec.hive.assistant.web.internal.v1.data;

import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateInputDTO;
import java.util.Set;

public class PromptTemplateDTODataFactory {

    public static PromptTemplateInputDTO createInputDTO() {
        return new PromptTemplateInputDTO(
            "TEST_CODE",
            "Test Prompt",
            "Description",
            Set.of("param1", "param2")
        );
    }
}
