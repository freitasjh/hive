package br.com.systec.hive.agent.web.internal.v1.dto;

import java.util.List;

public record FilteredModelDTO(String id, String reason) {

    public static List<FilteredModelDTO> of(List<br.com.systec.hive.provider.api.FilteredModel> filtered) {
        if (filtered == null) {
            return List.of();
        }
        return filtered.stream()
                .map(f -> new FilteredModelDTO(f.id(), f.reason()))
                .toList();
    }
}
