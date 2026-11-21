package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.agent.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.api.util.ProviderOverrideGuard;
import br.com.systec.hive.history.impl.entity.ChatSessionEntity;
import br.com.systec.hive.agent.impl.mapper.ChatSessionMapperDomain;
import br.com.systec.hive.agent.impl.mapper.PromptTemplateDomainMapper;
import br.com.systec.hive.agent.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.history.impl.repository.ChatSessionRepository;
import br.com.systec.hive.agent.impl.util.ChatTitleUtil;
import br.com.systec.hive.provider.api.AiProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ChatSessionMapperDomain mapper;
    private final PromptTemplateDomainMapper promptTemplateMapper;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository,
                                  PromptTemplateRepository promptTemplateRepository,
                                  ChatSessionMapperDomain mapper,
                                  PromptTemplateDomainMapper promptTemplateMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.promptTemplateRepository = promptTemplateRepository;
        this.mapper = mapper;
        this.promptTemplateMapper = promptTemplateMapper;
    }

    @Override
    @Transactional
    public ChatSession create(UUID promptTemplateId, String title, UUID tenantId, UUID userId) {
        return create(promptTemplateId, title, tenantId, userId, null, null);
    }

    @Override
    @Transactional
    public ChatSession create(UUID promptTemplateId, String title, UUID tenantId, UUID userId,
                              AiProvider provider, String model) {
        // O MESMO guard do turno: metade de par ou modelo fora do catálogo é 400 aqui, antes da
        // consulta do template e antes do INSERT. Uma sessão inválida não poderia ser corrigida
        // depois — o turno com sessionId ignora o payload.
        ProviderOverrideGuard.validate(provider, model);
        // The template is validated on the way in, not just by the FK. A session pointing at another
        // tenant's template is a 404 here and a confusing generation failure later; and the read
        // carries the tenant, so a foreign id is indistinguishable from a missing one (anti-IDOR).
        promptTemplateRepository.findByIdAndTenantId(promptTemplateId, tenantId)
                .orElseThrow(() -> new PromptTemplateNotFoundException(promptTemplateId));

        String truncatedTitle = ChatTitleUtil.truncate(title, 50);

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setUserId(userId);
        entity.setPromptTemplateId(promptTemplateId);
        // NULL/NULL = usar o default do tenant; o par não é preenchido "por conveniência".
        entity.setProvider(provider == null ? null : provider.name());
        entity.setModel(model);
        entity.setTitle(truncatedTitle);
        entity.setTenantId(tenantId);

        ChatSessionEntity saved = chatSessionRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Page<ChatSession> listSessions(UUID tenantId, UUID userId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updateAt"));
        Page<ChatSessionEntity> page = chatSessionRepository.findByTenantIdAndUserId(tenantId, userId, sorted);
        return page.map(mapper::toDomain);
    }

    @Override
    public ChatSession getSession(UUID id, UUID tenantId, UUID userId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PromptTemplate resolvePromptTemplate(UUID sessionId, UUID tenantId, UUID userId) {
        ChatSessionEntity session = chatSessionRepository.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        return promptTemplateRepository.findByIdAndTenantId(session.getPromptTemplateId(), tenantId)
                .map(promptTemplateMapper::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteSession(UUID id, UUID tenantId, UUID userId) {
        chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        chatSessionRepository.deleteByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional
    public void touchUpdateAt(UUID sessionId, UUID tenantId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        entity.setUpdateAt(LocalDateTime.now());
        chatSessionRepository.save(entity);
    }

    @Override
    @Transactional
    public ChatSession updateTitle(UUID id, String title, UUID tenantId, UUID userId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        String truncated = ChatTitleUtil.truncate(title, 50);
        entity.setTitle(truncated);
        entity.setUpdateAt(LocalDateTime.now());
        ChatSessionEntity saved = chatSessionRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
