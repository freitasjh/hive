package br.com.systec.hive.assistant.api.domain;

import br.com.systec.hive.provider.api.AiProvider;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public class ProviderCredential implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private AiProvider provider;
    private boolean configured;
    private String last4;
    private String baseUrl;
    private LocalDateTime updatedAt;

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getLast4() {
        return last4;
    }

    public void setLast4(String last4) {
        this.last4 = last4;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
