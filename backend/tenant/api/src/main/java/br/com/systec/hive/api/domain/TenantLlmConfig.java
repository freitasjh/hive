package br.com.systec.hive.api.domain;

import br.com.systec.hive.commons.api.domain.BaseDomain;
import br.com.systec.hive.provider.api.AiProvider;

import java.io.Serial;

/**
 * The tenant's one and only default LLM: which vendor, which model.
 *
 * <p>It is a parameter of the <b>tenant</b>, not of a user and not of a conversation — the prompt
 * is what a conversation chooses, the model is what the tenant bought. A conversation therefore has
 * nothing to configure here, and there is no per-user and no global default: exactly one row per
 * tenant, and no row is an error that names what to configure rather than a silent fallback.
 */
public class TenantLlmConfig extends BaseDomain {

    @Serial
    private static final long serialVersionUID = -4820114401937120041L;

    private AiProvider provider;
    private String model;

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
