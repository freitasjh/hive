package br.com.systec.hive.provider.api;

import java.time.Instant;
import java.util.List;

public record DiscoveredModels(List<String> models, List<FilteredModel> filteredOut, Instant cachedAt) {

    public DiscoveredModels {
        models = models == null ? List.of() : List.copyOf(models);
        filteredOut = filteredOut == null ? List.of() : List.copyOf(filteredOut);
    }
}
