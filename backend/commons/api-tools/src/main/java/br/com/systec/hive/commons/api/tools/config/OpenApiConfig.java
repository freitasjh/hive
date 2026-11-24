package br.com.systec.hive.commons.api.tools.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("1-INTERNAL")
                .displayName("🔌 APIs INTERNAS (Web/Mobile)")
                .pathsToMatch("/v1/**")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hive")
                        .version("1.0.0")
                        .description("### Documentação Técnica e Inventário de APIs\n" +
                                "Sistema para utilização de Assistente e Agentes de IA.\n\n" +
                                "--- \n" +
                                "**Dicas de Visualização:**\n" +
                                "- Utilize o seletor no topo para trocar entre APIs Internas e Externas.\n" +
                                "- Todos os modelos seguem precisão decimal conforme diretrizes do projeto.")
                        .contact(new Contact()
                                .name("Engenharia OpusFinancial")
                                .email("dev@opusfinancial.com"))
                        .license(new License()
                                .name("Uso Interno - Systec")
                                .url("https://systec.com.br")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-key"))
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        // ADR-001/key: esquema adicional; bearer-key permanece inalterado.
                        .addSecuritySchemes("apiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("sk-")));
    }
}
