package br.com.systec.hive.api.domain;

import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantLlmConfigProvidersTest {

    @Test
    void when_providerIsOnTheList_then_allowed() {
        // Todos os membros do enum estão na allowlist hoje. A asserção é sobre a lista, não sobre o
        // enum: quando alguém adicionar um membro ao enum, este teste quebra e a decisão é tomada
        // em vez de herdada.
        for (AiProvider provider : AiProvider.values()) {
            assertThat(TenantLlmConfigProviders.isAllowed(provider))
                    .as("provider %s precisa de uma decisão explícita na allowlist", provider)
                    .isTrue();
        }
    }

    @Test
    void when_providerIsNull_then_notAllowed() {
        // Null é "não informado", não "informado e válido": um default aceito aqui viraria um chat
        // sem vendor, e a falha apareceria como erro do LangChain4j muito depois de onde foi cometida.
        assertThat(TenantLlmConfigProviders.isAllowed(null)).isFalse();
    }

    @Test
    void when_allowed_then_theSetIsImmutable() {
        // Uma denylist falha aberta: o próximo membro adicionado ao enum seria aceito no dia em que
        // compila, sem ninguém ter decidido nada. Set.of congela a lista, então a garantia sobrevive
        // a um ALLOWED.add(...) distraído.
        assertThatThrownByAdd();
    }

    private void assertThatThrownByAdd() {
        try {
            TenantLlmConfigProviders.allowed().add(AiProvider.OPENAI);
            throw new AssertionError("a allowlist não deveria aceitar add()");
        } catch (UnsupportedOperationException expected) {
            assertThat(TenantLlmConfigProviders.allowed()).hasSize(AiProvider.values().length);
        }
    }
}
