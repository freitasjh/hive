package br.com.systec.hive.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-001 (RF3): group {@code public} restrito a {@code /v1/public/**}.
 *
 * <p>O group descreve os endpoints públicos; o acesso ao documento permanece autenticado na
 * chain humana (Decisão 6). Nenhum outro group é declarado.
 */
@Configuration
public class PublicApiOpenApiConfig {

    @Bean
    public GroupedOpenApi publicApiOpenApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/v1/public/**")
                .build();
    }
}
