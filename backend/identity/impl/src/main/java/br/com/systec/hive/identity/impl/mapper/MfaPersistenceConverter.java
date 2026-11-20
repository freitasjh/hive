package br.com.systec.hive.identity.impl.mapper;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.impl.entity.MfaRecoveryCodeEntity;
import br.com.systec.hive.identity.impl.entity.MfaSecretEntity;

/**
 * Conversor estático entre entidades JPA e os dados da porta de persistência.
 * Construtor privado + métodos {@code of()} — sem MapStruct, conforme padrão do repo.
 */
public final class MfaPersistenceConverter {

    private MfaPersistenceConverter() {
    }

    public static MfaSecretData of(MfaSecretEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MfaSecretData(
                entity.getUserId(),
                entity.getTenantId(),
                entity.getSecretCiphertext(),
                entity.isEnabled(),
                entity.getConfirmedAt()
        );
    }

    public static MfaSecretEntity of(MfaSecretData data) {
        if (data == null) {
            return null;
        }
        MfaSecretEntity entity = new MfaSecretEntity();
        entity.setUserId(data.userId());
        entity.setTenantId(data.tenantId());
        entity.setSecretCiphertext(data.secretCiphertext());
        entity.setEnabled(data.enabled());
        entity.setConfirmedAt(data.confirmedAt());
        return entity;
    }

    public static MfaRecoveryCodeData of(MfaRecoveryCodeEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MfaRecoveryCodeData(
                entity.getId(),
                entity.getUserId(),
                entity.getCodeHash(),
                entity.getUsedAt(),
                entity.getCreatedAt()
        );
    }

    public static MfaRecoveryCodeEntity of(MfaRecoveryCodeData data) {
        if (data == null) {
            return null;
        }
        MfaRecoveryCodeEntity entity = new MfaRecoveryCodeEntity();
        entity.setId(data.id());
        entity.setUserId(data.userId());
        entity.setCodeHash(data.codeHash());
        entity.setUsedAt(data.usedAt());
        if (data.createdAt() != null) {
            entity.setCreatedAt(data.createdAt());
        }
        return entity;
    }
}
