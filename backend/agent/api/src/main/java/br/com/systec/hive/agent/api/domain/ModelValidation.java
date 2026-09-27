package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.provider.api.AiProvider;

import java.io.Serial;
import java.io.Serializable;

/**
 * The result of probing a vendor with a model: did it answer, how long did it take, and if not, why.
 *
 * <p>Named for what it is rather than for what used to own it. It was {@code AgentValidation} back
 * when the probe existed to check a field on the agent editor, and it outlived that screen by two
 * years of being the only thing the credentials screen could call.
 */
public class ModelValidation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean ok;
    private AiProvider provider;
    private String model;
    private long latencyMs;
    private String reason;

    public static ModelValidation ok(AiProvider provider, String model, long latencyMs) {
        ModelValidation validation = new ModelValidation();
        validation.ok = true;
        validation.provider = provider;
        validation.model = model;
        validation.latencyMs = latencyMs;
        return validation;
    }

    public static ModelValidation failed(AiProvider provider, String model, long latencyMs, String reason) {
        ModelValidation validation = new ModelValidation();
        validation.ok = false;
        validation.provider = provider;
        validation.model = model;
        validation.latencyMs = latencyMs;
        validation.reason = reason;
        return validation;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

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

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
