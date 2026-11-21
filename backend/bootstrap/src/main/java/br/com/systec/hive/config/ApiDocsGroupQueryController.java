package br.com.systec.hive.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * ADR-001 (gate CT01/CT02): aceita {@code GET /v3/api-docs?group=public} além do formato canônico
 * do springdoc {@code /v3/api-docs/{group}}.
 *
 * <p>No springdoc 3.2.0 o group é um {@code @PathVariable} (confirmado no bytecode de
 * {@code MultipleOpenApiWebMvcResource} e na FAQ oficial); uma query {@code ?group=} Sozinha cai no
 * documento default. Este alias apenas delega ao recurso do próprio springdoc — não reimplementa
 * geração de spec, não cria outro group e não altera o documento canônico. O acesso continua
 * autenticado na chain humana (Decisão 6).
 */
@RestController
public class ApiDocsGroupQueryController {

    private final MultipleOpenApiWebMvcResource springdocResource;

    public ApiDocsGroupQueryController(MultipleOpenApiWebMvcResource springdocResource) {
        this.springdocResource = springdocResource;
    }

    @GetMapping(path = "/v3/api-docs", params = "group")
    public ResponseEntity<byte[]> openApiByGroupQuery(HttpServletRequest request,
                                                       @RequestParam("group") String group,
                                                       Locale locale) throws JsonProcessingException {
        // MultipleOpenApiWebMvcResource.openapiJson(request, apiVersion, group, locale): group é o 3º argumento.
        byte[] document = springdocResource.openapiJson(request, null, group, locale);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(document);
    }
}
