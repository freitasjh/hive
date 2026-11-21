package br.com.systec.hive.assistant.web.internal.v1.dto;

import java.time.Instant;
import java.util.List;

public record ProviderModelsDTO(List<String> models, List<FilteredModelDTO> filteredOut, Instant cachedAt) {
}
