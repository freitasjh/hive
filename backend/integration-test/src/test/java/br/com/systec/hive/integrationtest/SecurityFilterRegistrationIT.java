package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.security.impl.filter.SecurityFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do registro do filtro: usa HTTP real para exercitar o container e contar uma
 * execução do filtro por request — MockMvc sozinho não cobre esse caminho.
 */
class SecurityFilterRegistrationIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @LocalServerPort
    private int port;

    private final RestTemplate client = new RestTemplate();

    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void detachAppender() {
        if (appender != null) {
            ((Logger) LoggerFactory.getLogger(SecurityFilter.class)).detachAppender(appender);
            appender = null;
        }
    }

    @Test
    void when_docsRequestedOverRealHttpWithRefreshCookie_then_filterRunsOnceAndServes() {
        String username = "reg-cookie-" + UUID.randomUUID();
        seedTenantAndUser(UUID.randomUUID(), username, PASSWORD);
        String refreshCookie = loginAndGetRefreshCookie(username);

        attachLogCapture();
        ResponseEntity<String> docs = get("/v3/api-docs/public", refreshCookie);
        ResponseEntity<String> scalar = get("/scalar", refreshCookie);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scalar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startFilterMessages()).hasSize(2);
    }

    @Test
    void when_docsRequestedOverRealHttpWithoutCookie_then_unauthorized() {
        ResponseEntity<String> docs = get("/v3/api-docs/public", null);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/hive";
    }

    private ResponseEntity<String> get(String path, String refreshCookie) {
        HttpHeaders headers = new HttpHeaders();
        if (refreshCookie != null) {
            headers.add(HttpHeaders.COOKIE, "refreshToken=" + refreshCookie);
        }
        try {
            return client.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    private String loginAndGetRefreshCookie(String username) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = client.exchange(baseUrl() + "/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refreshToken=");
        return setCookie.substring(setCookie.indexOf("refreshToken=") + "refreshToken=".length(),
                setCookie.indexOf(';'));
    }

    private void attachLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private List<String> startFilterMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("Iniciando filtro de segurança"))
                .toList();
    }

}
