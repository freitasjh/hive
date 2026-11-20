package br.com.systec.hive.security.web.internal.v1.mapper;

import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.web.internal.v1.dto.LoginResponseDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** MapStruct real (nunca mock): garante que o sinal de onboarding chega ao DTO de resposta. */
class LoginMapperDTOTest {

    private final LoginMapperDTO mapper = Mappers.getMapper(LoginMapperDTO.class);

    @Test
    void when_toLoginResponse_withSetupAvailable_then_carriesBooleanFlag() {
        UUID userId = UUID.randomUUID();
        LoginAuthenticate authenticate = new LoginAuthenticate(userId, "access-token", "Bearer", "", "refresh");
        authenticate.setMfaSetupAvailable(true);

        LoginResponseDTO dto = mapper.toLoginResponse(authenticate);

        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.accessToken()).isEqualTo("access-token");
        assertThat(dto.type()).isEqualTo("Bearer");
        assertThat(dto.mfaSetupAvailable()).isTrue();
    }

    @Test
    void when_toLoginResponse_withoutSetupFlag_then_booleanIsFalse() {
        LoginAuthenticate authenticate =
                new LoginAuthenticate(UUID.randomUUID(), "access-token", "Bearer", "", "refresh");

        LoginResponseDTO dto = mapper.toLoginResponse(authenticate);

        assertThat(dto.mfaSetupAvailable()).isFalse();
    }
}
