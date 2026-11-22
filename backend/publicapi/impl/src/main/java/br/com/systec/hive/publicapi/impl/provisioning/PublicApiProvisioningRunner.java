package br.com.systec.hive.publicapi.impl.provisioning;

import br.com.systec.hive.publicapi.api.domain.ProvisionedClient;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.api.exceptions.InvalidTenantIdException;
import br.com.systec.hive.publicapi.api.service.PublicApiClientService;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PublicApiProvisioningRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicApiProvisioningRunner.class);
    private static final String GRANT = PublicApiClientAccessWriter.PUBLIC_CHAT_GRANT;

    private final PublicApiClientService clientService;
    private final PublicApiClientRepository clientRepository;
    private final PublicApiClientAccessWriter accessWriter;

    @Value("${hive.publicapi.provision.enabled:false}")
    private boolean enabled;

    @Value("${hive.publicapi.provision.base-url:http://localhost:8080/hive/v1/public}")
    private String baseUrl;

    @Value("${hive.publicapi.provision.tenant-id:00000000-0000-0000-0000-000000000001}")
    private String tenantIdStr;

    @Value("${hive.publicapi.provision.label:default}")
    private String defaultLabel;

    @Value("${hive.publicapi.provision.prompt-codes:}")
    private String defaultPromptCodes;

    @Value("${hive.publicapi.provision.pin-mode:FOLLOW_ACTIVE}")
    private String defaultPinMode;

    private ProvisioningPayload lastPayload;

    public PublicApiProvisioningRunner(PublicApiClientService clientService,
                                      PublicApiClientRepository clientRepository,
                                      PublicApiClientAccessWriter accessWriter) {
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.accessWriter = accessWriter;
    }

    @Override
    public void run(String... args) {
        boolean shouldRun = enabled;
        boolean printSecret = false;
        for (String a : args) {
            if ("--provision".equals(a) || "--provision-public-api".equals(a)) {
                shouldRun = true;
            }
            if ("--print-secret".equals(a)) {
                printSecret = true;
            }
        }
        if (!shouldRun) {
            return;
        }
        ProvisioningPayload payload = provision(args);
        this.lastPayload = payload;
        printOnce(payload, printSecret);
        this.lastPayload = null;
    }

    @Transactional
    public ProvisioningPayload provision(String... args) {
        String label = arg(args, "--label=", defaultLabel);
        String tenantIdRaw = arg(args, "--tenant-id=", tenantIdStr);
        String promptCodesRaw = arg(args, "--prompt-codes=", defaultPromptCodes);
        String pinMode = arg(args, "--pin-mode=", defaultPinMode);
        if (pinMode == null || pinMode.trim().isEmpty()) {
            pinMode = "FOLLOW_ACTIVE";
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantIdRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidTenantIdException(tenantIdRaw, e);
        }
        ProvisionedClient pc = clientService.provision(tenantId, label);

        // grant + prompts: mesma regra da API admin (ADR-003, Decisão 2c).
        List<String> codes = new ArrayList<>();
        for (String c : promptCodesRaw.split(",")) {
            String code = c.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        final List<String> codeList = List.copyOf(codes);
        final String effectivePinMode = pinMode;
        List<PublicApiClientAccessWriter.PromptBinding> bindings = codeList.stream()
                .map(code -> new PublicApiClientAccessWriter.PromptBinding(code, effectivePinMode, null))
                .toList();
        accessWriter.write(tenantId, findClientRef(pc.getClientId()), List.of(GRANT), bindings);

        ProvisioningPayload payload = ProvisioningPayloadBuilder.build(
                pc.getClientId(), pc.getClientSecret(), baseUrl, codes, pinMode,
                200, 32000, 256000);
        return payload;
    }

    public ProvisioningPayload getLastPayload() {
        return lastPayload;
    }

    private UUID findClientRef(String clientId) {
        PublicApiClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("provisioned client not found"));
        return entity.getId();
    }

    private void printOnce(ProvisioningPayload payload, boolean printSecret) {
        System.out.println("=== PUBLIC API PROVISIONING (ONCE) ===");
        System.out.println("client_id      = " + payload.getClientId());
        if (printSecret) {
            System.out.println("client_secret  = " + payload.getClientSecret());
        } else {
            System.out.println("client_secret  = *** (redacted; pass --print-secret to reveal)");
        }
        System.out.println("token_url      = " + payload.getTokenUrl());
        System.out.println("base_url       = " + payload.getBaseUrl());
        System.out.println("prompt_codes   = " + payload.getPromptCodes());
        System.out.println("limits         = " + payload.getLimits());
        System.out.println("pin_mode       = " + payload.getPinMode());
        System.out.println("=== END ===");
    }

    static String arg(String[] args, String prefix, String def) {
        for (String a : args) {
            if (a.startsWith(prefix)) {
                return a.substring(prefix.length());
            }
        }
        return def;
    }
}