package br.com.systec.hive.assistant.impl.startup;

import br.com.systec.hive.assistant.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.assistant.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.assistant.impl.security.ProviderMasterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Verificação de consistência da chave mestra de cifragem no startup.
 *
 * <p>Com a chave divergente da que cifrou, todo endpoint que lê credencial responde 500 com
 * mensagem genérica e a causa real só aparece no log de request — o que já gerou diagnóstico
 * de "3 bugs críticos" inexistentes. Aqui a falha aparece <strong>uma vez</strong>, no boot, com
 * a mesma orientação de {@link AesGcmAttributeConverter#MISMATCH_MESSAGE}.
 *
 * <p>A chave é a mesma resolvida por {@link ProviderMasterKey} no boot (variável de ambiente
 * &gt; propriedade Spring), que já falha cedo se estiver ausente ou malformada. Aqui só interessa
 * a divergência: chave presente, mas diferente da que cifrou o que está gravado.
 *
 * <p>Nunca interrompe o startup: um erro aqui é de configuração do ambiente, não do código.
 * A aplicação sobe e o log aponta a causa.
 */
@Component
public class CredentialMasterKeyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredentialMasterKeyStartupCheck.class);

    private final ProviderCredentialRepository repository;
    private final ProviderMasterKey masterKey;
    private final AesGcmAttributeConverter converter = new AesGcmAttributeConverter();

    public CredentialMasterKeyStartupCheck(ProviderCredentialRepository repository,
                                           ProviderMasterKey masterKey) {
        this.repository = repository;
        this.masterKey = masterKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<String> ciphertext;
        try {
            ciphertext = repository.findFirstRawCiphertext();
        } catch (Exception e) {
            // Falha de infra (banco indisponível, tabela ausente) não é divergência de chave.
            // Precisa ser separada da leitura, senão um IllegalStateException do repositório
            // seria reportado como chave incompatível.
            log.warn("Não foi possível verificar a chave mestra no startup (origem: {}): {}",
                    masterKey.sourceDescription(), e.getClass().getSimpleName());
            return;
        }
        if (ciphertext.isEmpty()) {
            return;
        }
        try {
            // Se decifrar, a chave está correta. O valor decifrado é descartado sem uso.
            converter.convertToEntityAttribute(ciphertext.get());
        } catch (IllegalStateException e) {
            // Chave divergente das credenciais gravadas: a mensagem do converter já é a orientação.
            log.error("\n"
                    + "=====================================================================\n"
                    + " CHAVE MESTRA INCOMPATÍVEL COM AS CREDENCIAIS GRAVADAS\n"
                    + " Origem: {}\n"
                    + " {}\n"
                    + "=====================================================================",
                    masterKey.sourceDescription(), e.getMessage());
        }
    }
}
