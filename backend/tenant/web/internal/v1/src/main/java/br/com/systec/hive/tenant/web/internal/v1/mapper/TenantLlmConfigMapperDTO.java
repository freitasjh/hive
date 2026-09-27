package br.com.systec.hive.tenant.web.internal.v1.mapper;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.tenant.web.internal.v1.dto.TenantLlmConfigDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TenantLlmConfigMapperDTO {

    /**
     * {@code tenantId} vem do chamador e não do VO.
     *
     * <p>{@link TenantLlmConfig} estende {@code BaseDomain} e não tem {@code setTenantId} — não há
     * como um payload carregar o tenant para dentro do objeto. A tela precisa do id para mostrar de
     * quem é a configuração, então o controller, que acabou de tirá-lo do principal autenticado,
     * passa explicitamente. Um método separado para isso seria uma assinatura quase igual; um
     * parâmetro a mais deixa a origem do valor óbvia na chamada.
     */
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "createAt", expression = "java(toInstant(domain.getCreateAt()))")
    @Mapping(target = "updateAt", expression = "java(toInstant(domain.getUpdateAt()))")
    TenantLlmConfigDTO toDto(TenantLlmConfig domain, UUID tenantId);

    default Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
