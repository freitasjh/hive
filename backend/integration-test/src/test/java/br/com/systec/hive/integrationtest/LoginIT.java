package br.com.systec.hive.integrationtest;

import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Test
    void when_loginWithUnknownUser_then_returns401InvalidCredentials() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"noexiste_xyz\",\"password\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }

    @Test
    void when_loginWithWrongPassword_then_returns401WithoutLeakingExistence() throws Exception {
        UUID seededTenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(seededTenantId, username, PASSWORD);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"senha_errada_123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }

    @Test
    void when_loginWithValidCredentials_then_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void when_login_then_tokenCarriesExactlyTheDeclaredDefaultPermissions() throws Exception {
        // `SecurityTokenServiceImpl.generateToken` tem a lista de permissões hardcoded e NÃO deriva de
        // `SecurityConstants.DEFAULT_PERMISSIONS` — este módulo (`security/impl`) não depende de
        // `commons-api-tools`, e a dependência entre módulos para evitar seis palavras repetidas não
        // se paga. A duplicação, porém, já custou um bug: as duas permissões de conhecimento saíram das
        // constantes e ficaram na lista, e todo token passou a conceder capabilities inexistentes.
        //
        // Este teste é a barreira: ele compara o claim `permissions` de um token REAL com a lista de
        // constantes, e falha se os dois divergirem em qualquer direção. Adicionar uma permissão sem
        // concedê-la (ou conceder sem declarar) quebra aqui.
        UUID tenantId = UUID.randomUUID();
        String username = "it-user-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);

        String accessToken = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String jwt = JsonPath.read(accessToken, "$.accessToken");
        Claim permissions = JWT.decode(jwt).getClaim("permissions");
        List<String> granted = permissions.asList(String.class);

        assertThat(granted).containsExactlyInAnyOrderElementsOf(SecurityConstants.DEFAULT_PERMISSIONS);
    }
}
