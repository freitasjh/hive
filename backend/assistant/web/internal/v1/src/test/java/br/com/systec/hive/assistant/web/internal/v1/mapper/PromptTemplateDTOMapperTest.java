package br.com.systec.hive.assistant.web.internal.v1.mapper;

import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.web.internal.v1.data.PromptTemplateDTODataFactory;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateInputDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateDTOMapperTest {

    private final PromptTemplateDTOMapper mapper = Mappers.getMapper(PromptTemplateDTOMapper.class);

    @Test
    void when_toDomain_then_success() {
        PromptTemplateInputDTO dto = PromptTemplateDTODataFactory.createInputDTO();
        PromptTemplate domain = mapper.toDomain(dto);

        assertThat(domain).isNotNull();
        assertThat(domain.getCode()).isEqualTo(dto.code());
        assertThat(domain.getParams()).isEqualTo(dto.params());
    }
}
