package br.com.systec.hive.assistant.api.util;

import br.com.systec.hive.assistant.api.exceptions.ProviderOverrideException;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;

/**
 * Única regra de validação do par (provider, model) escolhido para uma conversa.
 *
 * <p>Um override é atômico: os dois campos vêm juntos ou nenhum vem. Metade de um par seria
 * persistida numa sessão e herdada por todos os turnos seguintes — e como a sessão é autoritativa,
 * o cliente não conseguiria corrigir isso reenviando o payload. O catálogo é validado no mesmo
 * ponto, antes de qualquer escrita e antes da credencial ser lida (400 antes de 422).
 *
 * <p>Fonte da decisão (deepsearch 2026-11-20):
 * <a href="https://ui.shadcn.com/docs/ai-elements/base/model-selector">shadcn Model Selector</a>
 * — o seletor envia {@code model} no body da request; este guard é onde essa escolha vira um par
 * válido, tanto no turno quanto na criação da sessão.
 */
public final class ProviderOverrideGuard {

    private ProviderOverrideGuard() {
    }

    public static void validate(AiProvider provider, String model) {
        if ((provider == null) != (model == null)) {
            throw new ProviderOverrideException();
        }
        if (provider != null) {
            VendorCatalog.validate(provider, model);
        }
    }
}
