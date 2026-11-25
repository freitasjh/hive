package br.com.systec.hive.retention;

import java.net.http.HttpClient;

/** Entrypoint da CLI standalone. Nenhum framework, nenhum agendador in-process, nenhum Spring. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        RetentionConfig config;
        try {
            config = RetentionConfig.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("configuracao invalida: " + e.getMessage());
            System.exit(2);
            return;
        }
        LangfuseClient client = new LangfuseClient(config.baseUrl(), config.credentials(), HttpClient.newHttpClient());
        System.exit(new RetentionJob(config, client).run(System.out));
    }
}
