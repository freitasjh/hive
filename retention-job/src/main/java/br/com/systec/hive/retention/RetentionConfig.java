package br.com.systec.hive.retention;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Configuracao do job: tudo vem de ambiente; nenhuma credencial e literal em codigo. */
public record RetentionConfig(URI baseUrl, ApiCredentials credentials, int retentionDays, Instant now,
                              Path lockFile, String runId) {
    public static RetentionConfig fromEnvironment() {
        String base = required("LANGFUSE_BASE_URL");
        String auth = required("LANGFUSE_RETENTION_AUTH");
        String days = required("RETENTION_DAYS");
        int parsed;
        try {
            parsed = Integer.parseInt(days);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("RETENTION_DAYS deve ser inteiro");
        }
        if (parsed <= 0) throw new IllegalArgumentException("RETENTION_DAYS deve ser positivo");
        String runId = System.getenv("RUN_ID");
        return new RetentionConfig(URI.create(base), new ApiCredentials(auth), parsed, Instant.now(),
                Path.of(System.getenv().getOrDefault("RETENTION_LOCK_FILE", "/tmp/hive-langfuse-retention.lock")),
                runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId);
    }

    /** Cutoff determinstico: meia-noite UTC de hoje menos N dias; reexecucao no mesmo dia usa o mesmo limite. */
    public Instant cutoff() {
        return now.truncatedTo(ChronoUnit.DAYS).minus(retentionDays, ChronoUnit.DAYS);
    }

    /** Limite inferior inclusivo explicito; nao depende de default do servidor. */
    public Instant fromStartTime() {
        return Instant.EPOCH;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("variavel obrigatoria ausente: " + name);
        return value;
    }

    @Override
    public String toString() {
        return "RetentionConfig[baseUrl=" + baseUrl + ", retentionDays=" + retentionDays + ", runId=" + runId + "]";
    }
}
