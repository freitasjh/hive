# BUG — TLS hostname verification sempre falha no `PinnedHttpClient`

- **Data:** 2026-09-25
- **Severidade:** 🔴 P1 (bloqueia discovery GO/Zen e qualquer fetch HTTPS pinado: KB ingestion, SSRF guard)
- **Tipo:** backend / `commons/impl` / segurança
- **Status:** Aprovado em B1 (usuário: "Aprovado") → delegar B2

## Bug

Todo fetch HTTPS feito por `PinnedHttpClient` falha com
`IOException: TLS hostname verification failed for <host>`, mesmo com certificado
válido e cadeia confiável. Efeito observado: `GET /hive/v1/providers/go/models`
responde `422 DISCOVERY_FAILED` para `OPENCODE_GO` e `OPENCODE_ZEN`, apesar das
chaves estarem cadastradas. Clientes LangChain4j (que usam HttpClient próprio)
continuam funcionando — por isso o bug ficou mascarado no harness anterior.

## Causa raiz

Arquivo: `backend/commons/impl/src/main/java/br/com/systec/hive/commons/impl/http/PinnedHttpClient.java`
Método: `wrapTls(Socket, String sniHost, int port)`

O código faz verificação manual de hostname **após** o handshake:

```java
ssl.startHandshake();
if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(sniHost, ssl.getSession())) {
    throw new IOException("TLS hostname verification failed for " + sniHost);
}
```

`HttpsURLConnection.getDefaultHostnameVerifier()` é um stub que **nega sempre**.
JDK 25, `java.base/javax/net/ssl/HttpsURLConnection.java:198-204`:

```java
private static class DefaultHostnameVerifier implements HostnameVerifier {
    @Override
    public boolean verify(String hostname, SSLSession session) {
        return false;
    }
}
```

A verificação real nunca ocorreu ali. Prova isolada (Java 25.0.2, `opencode.ai`
e `www.google.com`, certificados Google Trust Services válidos, cadeia verificada
pelo truststore): `verify=false` nos **dois** hosts. O certificado real tem
`SAN = [opencode.ai, stats.opencode.ai, *.stats.opencode.ai]` — ou seja, o
hostname casaria; quem nega é o stub.

Consequência: a proteção contra SSRF via pinning + verificação de hostname está
**inativa**; qualquer destino alcançável falharia, e um destino com certificado
válido-but-different passaria pelo stub apenas se alguém alterasse o verificador
global. O `catch (IllegalArgumentException)` em torno de `setServerNames` é
intencional e deve ser preservado (literais de IP não são SNI legal).

## Solução proposta

1. Mover a verificação para dentro do handshake, usando endpoint identification:
   - `SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` — o provider
     valida cadeia + hostname durante `startHandshake()` e lança
     `SSLHandshakeException` em caso de falha.
   - Preservar `params.setServerNames(List.of(new SNIHostName(sniHost)))` e o
     `catch (IllegalArgumentException)` para IP literals.
2. Remover a chamada a `getDefaultHostnameVerifier().verify(...)` e a exception
   customizada (o `SSLHandshakeException` já carrega o motivo).
3. **Não** usar `HttpsURLConnection.setDefaultHostnameVerifier(...)` — é estado
   global de JVM, proibido aqui (efeito colateral em todo o processo).
4. Não afrouxar pinning: `assertRemotePinned`, `Host` header, redirect bloqueado,
   timeout e `maxBytes` permanecem intactos.

## Testes necessários

1. **Unit — `commons/impl`** (novo `PinnedHttpClientTlsTest`):
   - `when_get_with_plainHttpLocal_then_ok` — servidor HTTP local, sem TLS.
   - `when_get_with_httpsAndMatchingCert_then_ok` — `SSLServerSocket` local com
     keystore gerado no próprio teste (self-signed para `localhost`/SAN) e truststore
     própria; confirma que o handshake **passa** (regressão do bug).
   - `when_get_with_httpsAndMismatchedCert_then_throws` — certificado de `localhost`
     apresentado ao host esperado diferente; confirma `SSLHandshakeException`
     (prova que a verificação está ativa, não removida).
   - `assertRemotePinned` continua valendo em caso negativo.
   - Usar keystore/truststore temporários em `@TempDir`; não embutir keystore no repo.
2. **Regressão dos consumidores** — sem mudança de contrato:
   - `KnowledgeSsrfIT` (CGNAT, redirecionamento, IP literal, HTTPS remoto).
   - `KbPinnedConnectionTest` (existente em `knowledge/impl`) continua verde.
3. **IT do discovery** — `OpencodeGoZenIT` deve continuar cobrindo 422 para
   provider sem chave. Nenhum IT novo obrigatório; se a suíte tiver caso de
   discovery bem-sucedido com mock, reaproveitar.
4. Suites completas (H1): `mvn clean install` (79 unit + 55 IT, 0 falhas) e
   `npm run test` + `npm run build` (160).

## Riscos

- **Confiança indevida se o teste for removido junto com a verificação.** Mitigação:
  o caso `MismatchedCert_then_throws` é o que garante que a proteção permanece.
- `setEndpointIdentificationAlgorithm("HTTPS")` pode rejeitar hosts legados com
  SAN ausente que antes "funcionavam" — nesse caso estariam falhando hoje de todo
  modo (o stub já rejeita), logo não há regressão de comportamento observável.
- Afeta 3 consumidores (`OpencodeDiscoveryServiceImpl`, `KbIngestionService`,
  testes). Todos Fisheries devem rodar a suíte completa.
- Nenhuma mudança de schema, contrato HTTP ou migration.

## Critérios de aceite (H6)

- [ ] `mvn clean install` → 0 falhas (unit + IT)
- [ ] `PinnedHttpClientTlsTest` com caso negativo de hostname mismatch
- [ ] `KnowledgeSsrfIT` + `OpencodeGoZenIT` verdes
- [ ] `npm run test` + `npm run build` → 0 falhas
- [ ] `fullstack-code-reviewer` com zero 🔴
- [ ] `brain_store` da lição (scope `global` — bug de JDK aplicável a qualquer projeto)
- [ ] Processos `:8080`/`:5173` finalizados
