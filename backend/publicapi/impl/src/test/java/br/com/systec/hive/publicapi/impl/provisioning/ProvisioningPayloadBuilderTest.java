package br.com.systec.hive.publicapi.impl.provisioning;

import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningPayloadBuilderTest {

    @Test
    void when_build_then_populatesAllFields() {
        ProvisioningPayload p = ProvisioningPayloadBuilder.build(
                "cid", "secret", "http://base", List.of("code"), "FOLLOW_ACTIVE", 200, 32000, 256000);

        assertThat(p.getClientId()).isEqualTo("cid");
        assertThat(p.getClientSecret()).isEqualTo("secret");
        assertThat(p.getTokenUrl()).isEqualTo("http://base/auth/token");
        assertThat(p.getBaseUrl()).isEqualTo("http://base");
        assertThat(p.getPromptCodes()).containsExactly("code");
        assertThat(p.getLimits()).containsEntry("m", 200)
                .containsEntry("c", 32000)
                .containsEntry("t", 256000);
        assertThat(p.getPinMode()).isEqualTo("FOLLOW_ACTIVE");
    }
}