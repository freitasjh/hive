package br.com.systec.hive.assistant.impl.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Fonte única da chave mestra de cifragem (AES-256-GCM) das credenciais de provider.
 *
 * <p><strong>Resolução (uma única vez, no boot):</strong>
 * <ol>
 *   <li>variável de ambiente {@value #MASTER_KEY_ENV} — caminho seguro para produção/secret store;</li>
 *   <li>propriedade Spring {@value #MASTER_KEY_PROPERTY} — caminho declarativo, lida do
 *       {@link Environment} (portanto enxerga {@code application.properties} do perfil ativo).</li>
 * </ol>
 * Sem nenhuma das duas, o bean <strong>falha na inicialização</strong> com mensagem acionável.
 * Isso converte o antigo 500 em meio de requisição (difícil de diagnosticar) em erro de boot
 * imediato, e garante que a chave seja sempre a mesma entre execuções: trocá-la torna as
 * credenciais gravadas irrecuperáveis (AES-GCM não tem recuperação).
 *
 * <p><strong>Por que um campo estático.</strong> A chave mestra é <em>estado de processo</em>, não
 * estado de aplicação: é um segredo de instalação único da JVM. Além disso, o Hibernate cria a
 * instância do {@code AttributeConverter} fora do container Spring (construtor no-arg, via
 * {@code @Convert(converter = ...)}), portanto injetar o bean por construtor no converter seria
 * silenciosamente ignorado — o Hibernate usaria a sua própria instância. Publicar a chave resolvida
 * num ponto estático elimina essa classe de falha por construção: toda instância do converter, seja
 * criada pelo Hibernate ou não, resolve a mesma chave. A asserção que trava esse contrato é o
 * {@code MasterKeyDeclarativePathIT}, que decifra com uma instância crua do converter algo cifrado
 * apenas com a chave do bean.
 *
 * <p>A chave nunca aparece em {@code toString()}, log ou mensagem de erro.
 */
@Component
public class ProviderMasterKey {

    public static final String MASTER_KEY_ENV = "PROVIDER_MASTER_KEY";
    public static final String MASTER_KEY_PROPERTY = "provider.master.key";
    public static final int KEY_BYTES = 32;
    public static final String GENERATE_HINT = "openssl rand -base64 32";

    public static final String NOT_CONFIGURED_MESSAGE =
            "Chave mestra de cifragem das credenciais de provider não configurada. "
                    + "Defina a variável de ambiente " + MASTER_KEY_ENV
                    + " (caminho recomendado em produção) ou a propriedade Spring " + MASTER_KEY_PROPERTY
                    + ", com " + (KEY_BYTES * 8) + " bits em base64. Gere uma com: " + GENERATE_HINT;

    /**
     * Onde a chave veio. Exposto para diagnóstico seguro (nunca inclui a chave em si).
     */
    public enum Source {
        ENVIRONMENT_VARIABLE,
        SPRING_PROPERTY
    }

    private static volatile SecretKeySpec runtimeKey;

    private final Source source;
    private final SecretKeySpec secretKeySpec;

    @Autowired
    public ProviderMasterKey(Environment environment) {
        this(readEnvironmentVariable(), environment.getProperty(MASTER_KEY_PROPERTY));
    }

    /**
     * Ponto de extensão para teste: recebe as duas fontes já lidas, sem depender de
     * {@code System.getenv()} (que não é mutável em JVM).
     */
    ProviderMasterKey(String environmentVariable, String propertyValue) {
        if (isPresent(environmentVariable)) {
            this.source = Source.ENVIRONMENT_VARIABLE;
            this.secretKeySpec = decode(environmentVariable, MASTER_KEY_ENV);
        } else if (isPresent(propertyValue)) {
            this.source = Source.SPRING_PROPERTY;
            this.secretKeySpec = decode(propertyValue, MASTER_KEY_PROPERTY);
        } else {
            throw new IllegalStateException(NOT_CONFIGURED_MESSAGE);
        }
        runtimeKey = this.secretKeySpec;
    }

    private static String readEnvironmentVariable() {
        return System.getenv(MASTER_KEY_ENV);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static SecretKeySpec decode(String rawValue, String sourceReference) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(sourceReference + " não é um base64 válido. Gere uma com: "
                    + GENERATE_HINT, e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(sourceReference + " deve ter " + (KEY_BYTES * 8)
                    + "-bit (" + KEY_BYTES + " bytes) para AES-256-GCM, mas tem " + key.length
                    + " bytes. Gere uma com: " + GENERATE_HINT);
        }
        return new SecretKeySpec(key, "AES");
    }

    /**
     * Chave resolvida no boot. Somente leitura; a chave não é mutável.
     */
    public SecretKey secretKeySpec() {
        return secretKeySpec;
    }

    public Source source() {
        return source;
    }

    /**
     * Descrição segura da origem da chave, para logs de diagnóstico.
     */
    public String sourceDescription() {
        return switch (source) {
            case ENVIRONMENT_VARIABLE -> MASTER_KEY_ENV + " (variável de ambiente)";
            case SPRING_PROPERTY -> MASTER_KEY_PROPERTY + " (propriedade Spring)";
        };
    }

    /**
     * Chave instalada pelo bean no boot, ou {@code null} se o contexto ainda não subiu.
     * É o ponto de acesso do {@code AttributeConverter}, criado pelo Hibernate fora do Spring.
     */
    public static SecretKeySpec runtimeKey() {
        return runtimeKey;
    }

    /**
     * @apiNote Exclusivo para teste. Em produção a chave mestra dura o processo inteiro; limpá-la
     * tornaria as credenciais gravadas ilegíveis.
     */
    public static void resetRuntimeKeyForTesting() {
        runtimeKey = null;
    }

    @Override
    public String toString() {
        return "ProviderMasterKey{source=" + source + ", resolvedAtStartup=true}";
    }
}
