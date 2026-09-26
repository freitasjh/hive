package br.com.systec.hive.agent.web.internal.v1.mapper;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatSessionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ChatSessionMapperDTO {

    @Mapping(target = "createAt", expression = "java(toInstant(domain.getCreateAt()))")
    @Mapping(target = "updateAt", expression = "java(toInstant(domain.getUpdateAt()))")
    ChatSessionDTO toDto(ChatSession domain);

    default Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.toInstant(ZoneOffset.UTC);
    }

    default LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
