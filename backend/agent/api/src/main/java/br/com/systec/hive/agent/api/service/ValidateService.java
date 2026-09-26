package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.AgentValidation;
import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

public interface ValidateService {

    AgentValidation validate(UUID tenantId, AiProvider provider, String model, String baseUrl);
}
