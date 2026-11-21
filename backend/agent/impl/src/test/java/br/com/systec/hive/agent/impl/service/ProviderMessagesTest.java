package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.history.api.util.ProviderMessages;
import br.com.systec.hive.provider.api.ChatRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conversation converter, on its own.
 *
 * <p>The rule is that every caller of {@code ChatTurnGateway} sends the same prompt for the same
 * history: system entries stay in the list, assistant entries arrive as {@link AiMessage}, and an
 * entry with no text on either field is dropped instead of being sent as an empty message. The
 * class moved to {@code history/api} (ADR-017) and the internal chat path tests it
 * through {@link ChatServiceImplTest}; these cases assert the mapping itself, because the public
 * chat API will call the same static function with an empty history plus its own system prompt.
 */
class ProviderMessagesTest {

    private static ChatMessage message(ChatRole role, String content, String message) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        msg.setMessage(message);
        return msg;
    }

    @Test
    void when_converte_mensagem_system_entao_systemMessage_presente_na_lista() {
        List<dev.langchain4j.data.message.ChatMessage> result =
                ProviderMessages.toProviderMessages(List.of(
                        message(ChatRole.SYSTEM, "Voce e um assistente.", null)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(0).type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(((SystemMessage) result.get(0)).text()).isEqualTo("Voce e um assistente.");
    }

    @Test
    void when_converte_mensagem_assistant_entao_aiMessage_presente_na_lista() {
        List<dev.langchain4j.data.message.ChatMessage> result =
                ProviderMessages.toProviderMessages(List.of(
                        message(ChatRole.ASSISTANT, null, "Oi, como posso ajudar?")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(AiMessage.class);
        assertThat(result.get(0).type()).isEqualTo(ChatMessageType.AI);
        assertThat(((AiMessage) result.get(0)).text()).isEqualTo("Oi, como posso ajudar?");
    }

    @Test
    void when_mensagem_tem_content_e_message_nulos_entao_e_descartada() {
        List<dev.langchain4j.data.message.ChatMessage> result =
                ProviderMessages.toProviderMessages(List.of(
                        message(ChatRole.USER, null, null),
                        message(ChatRole.USER, "unica com texto", null)));

        assertThat(result).hasSize(1);
        assertThat(((dev.langchain4j.data.message.UserMessage) result.get(0)).singleText())
                .isEqualTo("unica com texto");
    }

    @Test
    void when_converte_historico_com_tres_papeis_entao_ordem_de_entrada_e_preservada() {
        List<dev.langchain4j.data.message.ChatMessage> result =
                ProviderMessages.toProviderMessages(List.of(
                        message(ChatRole.SYSTEM, "instrucao", null),
                        message(ChatRole.USER, null, "pergunta"),
                        message(ChatRole.ASSISTANT, null, "resposta")));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(result.get(1)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(result.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((dev.langchain4j.data.message.UserMessage) result.get(1)).singleText())
                .isEqualTo("pergunta");
        assertThat(((AiMessage) result.get(2)).text()).isEqualTo("resposta");
    }
}
