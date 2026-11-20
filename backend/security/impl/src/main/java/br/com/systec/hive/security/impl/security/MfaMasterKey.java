package br.com.systec.hive.security.impl.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Fonte única da chave mestra de cifragem do segredo MFA (AES-256-GCM).
 *
 * <p><strong>Chave dedicada.</strong> Esta chave é distinta de {@code provider.master.key}
 * ({@link #MASTER_KEY_ENV} / {@code mfa.master.key}), pela mesma razão pela qual credenciais de
 * provider e segredos TOTP não devem ser decifráveis juntos: isolamento de escopo e rotação
 * independente. Nunca reutilizar a chave de provider nem aceitar fallback embutido em código.
 *
 * <p><strong>Resolução (uma vez, no boot):</strong>
 * <ol>
 *   <li>variável de ambiente {@value #MASTER_KEY_ENV} — caminho seguro para produção/secret store;</li>
 *   <li>propriedade Spring {@value #MASTER_KEY_PROPERTY} — caminho declarativo (dev/test).</li>
 * </ol>
 * Sem nenhuma das duas:
 * <ul>
 *   <li>perfil {@code prod}: <strong>boot aborta</strong> (fail-closed);</li>
 *   <li>demais perfis: o bean sobe, mas qualquer operação de cifragem falha rápido —
 *       nenhuma chave "padrão" é inventada em memória.</li>
 * </ul>
 * A chave nunca aparece em {@code toString()}, log ou mensagem de erro.
 */
@Component
public class MfaMasterKey {

    public static final String MASTER_KEY_ENV = "MFA_MASTER_KEY";
    public static final String MASTER_KEY_PROPERTY = "mfa.master.key";
    public static final int KEY_BYTES = 32;
    public static final String GENERATE_HINT = "openssl rand -base64 32";

    public static final String NOT_CONFIGURED_MESSAGE =
            "Chave mestra de cifragem do segredo MFA não configurada. Defina a variável de ambiente "
                    + MASTER_KEY_ENV + " (obrigatório em produção) ou a propriedade Spring "
                    + MASTER_KEY_PROPERTY + ", com " + (KEY_BYTES * 8) + " bits em base64. Gere uma com: "
                    + GENERATE_HINT;

    private final SecretKeySpec secretKeySpec;
    private final boolean configured;

    @Autowired
    public MfaMasterKey(Environment environment) {
        this(readEnvironmentVariable(), environment.getProperty(MASTER_KEY_PROPERTY), isProd(environment));
    }

    /** Ponto de extensão para teste: recebe as fontes já lidas, sem depender de ambiente mutável. */
    MfaMasterKey(String environmentVariable, String propertyValue, boolean prodProfile) {
        String raw = present(environmentVariable) ? environmentVariable : propertyValue;
        if (!present(raw)) {
            if (prodProfile) {
                throw new IllegalStateException(NOT_CONFIGURED_MESSAGE);
            }
            this.secretKeySpec = null;
            this.configured = false;
            return;
        }
        this.secretKeySpec = decode(raw);
        this.configured = true;
    }

    private static boolean isProd(Environment environment) {
        // acceptsProfiles cobre perfis explicitamente ativos e, na ausência destes, os default.
        // getActiveProfiles() sozinho ignoraria spring.profiles.default=prod.
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private static String readEnvironmentVariable() {
        return System.getenv(MASTER_KEY_ENV);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static SecretKeySpec decode(String rawValue) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Chave MFA inválida: base64 não reconhecido. Gere uma com: " + GENERATE_HINT, e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("Chave MFA inválida: esperados " + (KEY_BYTES * 8)
                    + " bits (" + KEY_BYTES + " bytes), obtidos " + key.length
                    + " bytes. Gere uma com: " + GENERATE_HINT);
        }
        return new SecretKeySpec(key, "AES");
    }

    /** Chave somente leitura. Falha rápido se não configurada em perfil não-prod. */
    public SecretKey secretKeySpec() {
        if (!configured) {
            throw new IllegalStateException(NOT_CONFIGURED_MESSAGE);
        }
        return secretKeySpec;
    }

    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String toString() {
        return "MfaMasterKey{configured=" + configured + ", resolvedAtStartup=true}";
    }
}
