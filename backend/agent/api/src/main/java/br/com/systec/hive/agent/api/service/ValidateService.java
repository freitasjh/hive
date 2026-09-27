package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.ModelValidation;
import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

/**
 * Probes a vendor with a model without persisting anything. The screen that used it belonged to the
 * agent editor; the probe itself is about the provider, and that is where it now lives.
 */
public interface ValidateService {

    ModelValidation validate(UUID tenantId, AiProvider provider, String model, String baseUrl);
}
