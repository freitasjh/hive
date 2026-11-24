package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.publicapi.api.domain.ProvisionedClient;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreateDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientGrantsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPageDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPromptBindingDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPromptsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientResponseDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientRotatedDTO;
import br.com.systec.hive.publicapi.api.exceptions.DuplicateClientLabelException;
import br.com.systec.hive.publicapi.api.exceptions.PublicApiClientNotFoundException;
import br.com.systec.hive.publicapi.api.service.PublicApiClientAdminService;
import br.com.systec.hive.publicapi.api.service.PublicApiClientService;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientGrantEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientPromptEntity;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiClientAccessWriter;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientGrantRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientPromptRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** ADR-003/ADM01..ADM03: gestão administrativa de clients públicos na chain humana. */
@Service
public class PublicApiClientAdminServiceImpl implements PublicApiClientAdminService {

    private final PublicApiClientService clientService;
    private final PublicApiClientRepository clientRepository;
    private final PublicApiClientGrantRepository grantRepository;
    private final PublicApiClientPromptRepository promptRepository;
    private final PublicApiClientAccessWriter accessWriter;

    public PublicApiClientAdminServiceImpl(PublicApiClientService clientService,
                                           PublicApiClientRepository clientRepository,
                                           PublicApiClientGrantRepository grantRepository,
                                           PublicApiClientPromptRepository promptRepository,
                                           PublicApiClientAccessWriter accessWriter) {
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.grantRepository = grantRepository;
        this.promptRepository = promptRepository;
        this.accessWriter = accessWriter;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PublicApiClientCreatedDTO create(PublicApiClientCreateDTO request) {
        UUID tenantId = currentTenantId();
        if (clientRepository.findByTenantIdAndLabel(tenantId, request.getLabel()).isPresent()) {
            throw new DuplicateClientLabelException(request.getLabel());
        }
        ProvisionedClient provisioned = clientService.provision(tenantId, request.getLabel());
        PublicApiClientEntity entity = owned(tenantId, provisioned.getClientId());
        if (request.getTokenTtlSeconds() != null) entity.setTokenTtlSeconds(request.getTokenTtlSeconds());
        if (request.getMaxMessages() != null) entity.setMaxMessages(request.getMaxMessages());
        if (request.getMaxMessageChars() != null) entity.setMaxMessageChars(request.getMaxMessageChars());
        if (request.getMaxTotalChars() != null) entity.setMaxTotalChars(request.getMaxTotalChars());
        clientRepository.save(entity);

        accessWriter.write(tenantId, entity.getId(), request.getGrants(), bindings(request.getPrompts()));

        PublicApiClientCreatedDTO created = fill(new PublicApiClientCreatedDTO(), owned(tenantId, provisioned.getClientId()));
        created.setClientSecret(provisioned.getClientSecret());
        return created;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public PublicApiClientPageDTO list(int page, int size) {
        UUID tenantId = currentTenantId();
        Page<PublicApiClientEntity> result = clientRepository.findByTenantId(tenantId, PageRequest.of(page, size));
        List<PublicApiClientEntity> entities = result.getContent();
        List<java.util.UUID> refs = entities.stream().map(PublicApiClientEntity::getId).toList();

        // Bugfix N+1: duas queries para a página inteira, agrupadas por clientRef.
        Map<java.util.UUID, List<String>> grantsByRef = grantRepository.findByClientRefIn(refs).stream()
                .collect(Collectors.groupingBy(PublicApiClientGrantEntity::getClientRef,
                        Collectors.mapping(PublicApiClientGrantEntity::getPermission, Collectors.toList())));

        Map<java.util.UUID, List<PublicApiClientPromptBindingDTO>> promptsByRef =
                promptRepository.findByClientRefIn(refs).stream()
                        .collect(Collectors.groupingBy(PublicApiClientPromptEntity::getClientRef,
                                Collectors.mapping(p -> new PublicApiClientPromptBindingDTO(
                                        p.getPromptCode(), p.getPinMode(), p.getPinnedVersion()), Collectors.toList())));

        List<PublicApiClientResponseDTO> content = entities.stream()
                .map(entity -> fill(new PublicApiClientResponseDTO(), entity,
                        grantsByRef.getOrDefault(entity.getId(), List.of()),
                        promptsByRef.getOrDefault(entity.getId(), List.of())))
                .toList();
        return new PublicApiClientPageDTO(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public PublicApiClientResponseDTO get(String clientId) {
        UUID tenantId = currentTenantId();
        return fill(new PublicApiClientResponseDTO(), owned(tenantId, clientId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PublicApiClientRotatedDTO rotate(String clientId) {
        UUID tenantId = currentTenantId();
        owned(tenantId, clientId);
        String plaintext = clientService.rotateSecret(clientId);
        PublicApiClientEntity entity = owned(tenantId, clientId);
        PublicApiClientRotatedDTO dto = new PublicApiClientRotatedDTO();
        dto.setClientId(entity.getClientId());
        dto.setClientSecret(plaintext);
        dto.setPreviousSecretExpiresAt(entity.getPreviousSecretExpiresAt());
        return dto;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revoke(String clientId) {
        UUID tenantId = currentTenantId();
        owned(tenantId, clientId);
        clientService.revoke(clientId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PublicApiClientResponseDTO updateGrants(String clientId, PublicApiClientGrantsDTO request) {
        UUID tenantId = currentTenantId();
        PublicApiClientEntity entity = owned(tenantId, clientId);
        grantRepository.deleteAllByClientRef(entity.getId());
        grantRepository.flush();
        accessWriter.write(tenantId, entity.getId(), request.getGrants(), List.of());
        return fill(new PublicApiClientResponseDTO(), owned(tenantId, clientId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PublicApiClientResponseDTO updatePrompts(String clientId, PublicApiClientPromptsDTO request) {
        UUID tenantId = currentTenantId();
        PublicApiClientEntity entity = owned(tenantId, clientId);
        promptRepository.deleteAllByClientRef(entity.getId());
        promptRepository.flush();
        accessWriter.write(tenantId, entity.getId(), List.of(), bindings(request.getPrompts()));
        return fill(new PublicApiClientResponseDTO(), owned(tenantId, clientId));
    }

    private UUID currentTenantId() {
        try {
            UUID tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) throw new IllegalStateException("tenant ausente");
            return tenantId;
        } catch (RuntimeException e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }

    private PublicApiClientEntity owned(UUID tenantId, String clientId) {
        PublicApiClientEntity entity = clientRepository.findByClientId(clientId).orElse(null);
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new PublicApiClientNotFoundException();
        }
        return entity;
    }

    private static List<PublicApiClientAccessWriter.PromptBinding> bindings(List<PublicApiClientPromptBindingDTO> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(dto -> new PublicApiClientAccessWriter.PromptBinding(
                        dto.getPromptCode(), dto.getPinMode(), dto.getPinnedVersion()))
                .toList();
    }

    private <T extends PublicApiClientResponseDTO> T fill(T dto, PublicApiClientEntity entity) {
        List<String> grants = grantRepository.findByClientRef(entity.getId()).stream()
                .map(PublicApiClientGrantEntity::getPermission).toList();
        List<PublicApiClientPromptBindingDTO> prompts = promptRepository.findByClientRef(entity.getId()).stream()
                .map(p -> new PublicApiClientPromptBindingDTO(p.getPromptCode(), p.getPinMode(), p.getPinnedVersion()))
                .toList();
        return fill(dto, entity, grants, prompts);
    }

    private <T extends PublicApiClientResponseDTO> T fill(T dto, PublicApiClientEntity entity,
                                                          List<String> grants,
                                                          List<PublicApiClientPromptBindingDTO> prompts) {
        dto.setClientId(entity.getClientId());
        dto.setLabel(entity.getLabel());
        dto.setTenantId(entity.getTenantId());
        dto.setTokenTtlSeconds(entity.getTokenTtlSeconds());
        dto.setMaxMessages(entity.getMaxMessages());
        dto.setMaxMessageChars(entity.getMaxMessageChars());
        dto.setMaxTotalChars(entity.getMaxTotalChars());
        dto.setRevokedAt(entity.getRevokedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setPreviousSecretExpiresAt(entity.getPreviousSecretExpiresAt());

        dto.setGrants(grants);
        dto.setPrompts(prompts);
        return dto;
    }
}
