package br.com.systec.hive.assistant.web.internal.v1.mapper;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.web.internal.v1.dto.InferenceParamsDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateCreateDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateInfoDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateInputDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateSaveResponseDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateUpdateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PromptTemplateDTOMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "version", ignore = true)
    PromptTemplate toDomain(PromptTemplateCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "params", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "code", ignore = true)
    PromptTemplate toDomain(PromptTemplateUpdateDTO dto);

    @Mapping(target = "createAt", expression = "java(toInstant(domain.getCreateAt()))")
    @Mapping(target = "updateAt", expression = "java(toInstant(domain.getUpdateAt()))")
    @Mapping(target = "tags", expression = "java(toList(domain.getTags()))")
    PromptTemplateDTO toDTO(PromptTemplate domain);

    InferenceParamsDTO toDto(InferenceParams domain);

    InferenceParams toDomain(InferenceParamsDTO dto);

    void updateDomain(@MappingTarget PromptTemplate target, PromptTemplateCreateDTO dto);

    // Legacy compatibility for existing tests
    PromptTemplate toDomain(PromptTemplateInputDTO inputDTO);

    PromptTemplateSaveResponseDTO toSaveResponse(PromptTemplate domain);

    PromptTemplateInfoDTO toInfo(PromptTemplate domain);

    default Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.toInstant(ZoneOffset.UTC);
    }

    default List<String> toList(Set<String> set) {
        if (set == null) return null;
        return set.stream().collect(Collectors.toList());
    }

    default Set<String> toSet(List<String> list) {
        if (list == null) return null;
        return Set.copyOf(list);
    }
}
