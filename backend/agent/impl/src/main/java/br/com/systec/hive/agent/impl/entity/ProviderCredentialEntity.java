package br.com.systec.hive.agent.impl.entity;

import br.com.systec.hive.agent.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import br.com.systec.hive.provider.api.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.io.Serial;

@Entity
@Table(name = "provider_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_provider_credential_tenant_provider",
                columnNames = {"tenant_id", "provider"}))
public class ProviderCredentialEntity extends AbstractEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AiProvider provider;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name = "ciphertext", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Column(name = "base_url", length = 2048)
    private String baseUrl;

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(int keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
