package br.com.systec.hive.integrationtest;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.security.api.service.MfaCryptoPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT decisivo da Fase 1 MFA: migration V4 aplicada em Postgres real (pg16), persistência
 * JPA via porta de identity e nenhum segredo em claro no banco.
 */
class MfaPersistenceIT extends WebIntegrationTest {

    private static final String PLAIN_SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired
    private MfaRepositoryPort mfaRepository;

    @Autowired
    private MfaCryptoPort mfaCrypto;

    private UUID seedUser(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, "it-pw");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    }

    @Test
    void when_flywayRunsV4_then_tablesIndexAndForeignKeysExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('user_mfa', 'user_mfa_recovery_code')", String.class);
        assertThat(tables).containsExactlyInAnyOrder("user_mfa", "user_mfa_recovery_code");

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname = 'idx_mfa_recovery_user'", Integer.class);
        assertThat(indexCount).isEqualTo(1);

        // FKs declaradas na V4: recovery -> user_mfa -> users (cascade).
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_type = 'FOREIGN KEY' "
                        + "AND table_name IN ('user_mfa', 'user_mfa_recovery_code')", Integer.class);
        assertThat(fkCount).isEqualTo(2);

        String tenantNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'user_mfa' AND column_name = 'tenant_id'", String.class);
        assertThat(tenantNullable).isEqualTo("NO");
    }

    @Test
    void when_secretAndRecoveryCodesSaved_then_foundByUserAndTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = seedUser(tenantId, "mfa-save-" + UUID.randomUUID());
        String ciphertext = mfaCrypto.encrypt(PLAIN_SECRET);

        MfaSecretData saved = mfaRepository.save(new MfaSecretData(
                userId, tenantId, ciphertext, true, Instant.now()));
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.secretCiphertext()).isEqualTo(ciphertext);

        assertThat(mfaRepository.findByUserId(userId)).isPresent();
        assertThat(mfaRepository.findByUserIdAndTenantId(userId, tenantId)).isPresent();
        assertThat(mfaRepository.findByUserIdAndTenantId(userId, UUID.randomUUID())).isEmpty();
        assertThat(mfaRepository.existsByUserId(userId)).isTrue();

        UUID codeId = UUID.randomUUID();
        String hash = mfaCrypto.hashRecoveryCode("ABCDEF0123456789");
        mfaRepository.saveRecoveryCode(new MfaRecoveryCodeData(codeId, userId, hash, null, null));

        List<MfaRecoveryCodeData> codes = mfaRepository.findRecoveryCodesByUserId(userId);
        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).id()).isEqualTo(codeId);
        assertThat(codes.get(0).codeHash()).isEqualTo(hash);
        assertThat(codes.get(0).usedAt()).isNull();
        assertThat(codes.get(0).createdAt()).isNotNull();

        assertThat(mfaRepository.findRecoveryCodeById(codeId)).isPresent();
        Instant usedAt = Instant.now();
        mfaRepository.markRecoveryCodeUsed(codeId, usedAt);
        assertThat(mfaRepository.findRecoveryCodeById(codeId).orElseThrow().usedAt()).isNotNull();
    }

    @Test
    void when_secretPersisted_then_plaintextNeverStored() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = seedUser(tenantId, "mfa-plain-" + UUID.randomUUID());
        String ciphertext = mfaCrypto.encrypt(PLAIN_SECRET);
        mfaRepository.save(new MfaSecretData(userId, tenantId, ciphertext, false, null));

        String stored = jdbcTemplate.queryForObject(
                "SELECT secret_ciphertext FROM user_mfa WHERE user_id = ?", String.class, userId);

        assertThat(stored).isEqualTo(ciphertext);
        assertThat(stored).doesNotContain(PLAIN_SECRET);
        assertThat(mfaCrypto.decrypt(stored)).isEqualTo(PLAIN_SECRET);
    }

    @Test
    void when_deleteByUserId_then_cascadeRemovesRecoveryCodes() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = seedUser(tenantId, "mfa-delete-" + UUID.randomUUID());
        mfaRepository.save(new MfaSecretData(userId, tenantId, mfaCrypto.encrypt(PLAIN_SECRET), true, null));
        mfaRepository.saveRecoveryCode(new MfaRecoveryCodeData(
                UUID.randomUUID(), userId, mfaCrypto.hashRecoveryCode("ABCDEF0123456789"), null, null));

        mfaRepository.deleteByUserId(userId);

        assertThat(mfaRepository.findByUserId(userId)).isEmpty();
        assertThat(mfaRepository.findRecoveryCodesByUserId(userId)).isEmpty();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_mfa_recovery_code WHERE user_id = ?", Integer.class, userId);
        assertThat(count).isZero();
    }
}
