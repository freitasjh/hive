package br.com.systec.hive.assistant.web.internal.v1.mapper;

import br.com.systec.hive.assistant.web.internal.v1.dto.FilteredModelDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.ProviderModelsDTO;
import br.com.systec.hive.provider.api.DiscoveredModels;
import br.com.systec.hive.provider.api.FilteredModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProviderModelsDTOMapper {

    @Mapping(target = "filteredOut", source = "filteredOut", qualifiedByName = "toFilteredDto")
    ProviderModelsDTO toDto(DiscoveredModels domain);

    @Named("toFilteredDto")
    default List<FilteredModelDTO> toFilteredDto(List<FilteredModel> filtered) {
        return FilteredModelDTO.of(filtered);
    }
}
