package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** VER-11 / NFR-03: stack fixo Spring 7.0.8 via Boot 4.1.0; zero Flux/reactor/WebFlux/A2 silenciosa. */
class SseVersionGuardIT {

    @Test
    void when_dependenciesPomIsRead_then_bootIs410AndNoReactorModuleIsDeclared() throws IOException {
        Path pom = StreamingITFixtures.repoRoot().resolve("backend/dependencies/pom.xml");
        String text = Files.readString(pom);
        assertThat(text).contains("<spring.boot.version>4.1.0</spring.boot.version>");
        assertThat(text).doesNotContain("langchain4j-reactor", "spring-boot-starter-webflux", "reactor-core");
    }

    @Test
    void when_productionCodeIsScanned_then_noFluxReactorOrStreamingResponseBodyAppears() throws IOException {
        Path backend = StreamingITFixtures.repoRoot().resolve("backend");
        List<Path> sources;
        try (var stream = Files.walk(backend)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/src/test/"))
                    .toList();
        }
        for (Path source : sources) {
            String text = Files.readString(source);
            assertThat(text).as(source.toString())
                    .doesNotContain("Flux<", "reactor.core", "StreamingResponseBody");
        }
    }

    @Test
    void when_emitterContractIsScanned_then_onlyCompletionIsUsed() throws IOException {
        Path web = StreamingITFixtures.repoRoot().resolve("backend");
        List<Path> sources;
        try (var stream = Files.walk(web)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/src/test/"))
                    .filter(p -> p.toString().contains("/web/"))
                    .toList();
        }
        for (Path source : sources) {
            assertThat(Files.readString(source)).as(source.toString()).doesNotContain("completeWithError");
        }
    }
}
