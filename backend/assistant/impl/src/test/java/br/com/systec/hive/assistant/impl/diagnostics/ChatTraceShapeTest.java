package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTraceShapeTest {

    @Test
    void when_theRecordShapeIsInspected_then_itCarriesNoPromptOrResponseText() {
        Set<String> names = Arrays.stream(ChatTrace.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(names).contains("sessionId", "tenantId", "turnId", "clientId", "promptTemplateId",
                "provider", "model", "failure");
        assertThat(names).noneMatch(name -> name.toLowerCase().contains("prompttext")
                || name.toLowerCase().contains("response")
                || name.equalsIgnoreCase("prompt")
                || name.equalsIgnoreCase("answer"));
    }
}
