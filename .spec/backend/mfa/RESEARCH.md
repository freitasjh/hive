# MFA TOTP — RESEARCH (deepsearch)

> Consultas realizadas em 2026-11-19/20. Toda recomendação de lib/API/padrão cita URL + data.
> Regra aplicável: `.agents/rules/deepsearch.md` — nunca assumir assinatura de API de memória.

## 1. Spring Security 7 / MFA framework

- **Fonte**: https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html (consultado 2026-11-19)
- **Fonte**: https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7 (consultado 2026-11-19)
- Achado: o framework MFA do Spring Security 7 usa `FactorGrantedAuthority` (PASSWORD, X509, AUTHORIZATION_CODE, OTT) e `@EnableMultiFactorAuthentication`. **Não há fator TOTP nativo** — a implementação de TOTP é custom (artigo da making/ik.am confirma: "TOTP is not supported as a built-in Factor in Spring Security 7, so a custom implementation is required").
- Decisão: não adotar o framework MFA completo; manter o fluxo de tokens atual (JWT + refresh) e implementar o desafio como domínio de autenticação custom. Evita reescrever `SecurityConfig` e o contrato já entregue.

## 2. Biblioteca TOTP candidata — `dev.samstevens.totp:totp`

- **Fonte**: https://github.com/samdjstevens/java-totp (README, consultado 2026-11-20)
- **Fonte**: https://mvnrepository.com/artifact/dev.samstevens.totp/totp (consultado 2026-11-20)
- **Fonte**: https://deps.dev/maven/dev.samstevens.totp%3Atotp (consultado 2026-11-20)
- Versão mais recente: **1.7.1** (nov/2020). Requisito: **Java 8+** — compatível com Java 25. Licença MIT. Sem security advisories detectados.
- API verificada no README (assinaturas a conferir novamente no jar antes de codar):
  - `new DefaultSecretGenerator().generate()` → secret Base32 (32 chars).
  - `new QrData.Builder().label(...).secret(secret).issuer(...).algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build()`.
  - `new ZxingPngQrGenerator().generate(data)` → `byte[]` PNG; `getImageMimeType()`.
  - `new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider()).isValidCode(secret, code)`.
  - `verifier.setAllowedTimePeriodDiscrepancy(1)` para ±30 s.
- Dependências: `commons-codec 1.13`, `zxing core/javase 3.4.0`, `commons-net 3.6` (opcional, NTP — **não usar**; `SystemTimeProvider` basta).
- Risco: manutenção parada desde 2020 (OpenSSF 2.5/10, 0 commits em 90 dias) e dependências transitivas antigas. Mitigação: isolar atrás de `TotpPort` (interface no domínio) + adaptador; se o build/QA rejeitar, trocar o adaptador sem tocar no domínio. Alternativa a avaliar antes da implementação: `com.warrenstrange:googleauth` (verificar versão e advisories em deepsearch próprio).

## 3. Padrões internos reutilizáveis (fonte: repo)

| Padrão | Fonte |
|---|---|
| AES-256-GCM para segredo (provider credentials) | `backend/agent/impl/.../converter/AesGcmAttributeConverter.java`; `application-dev.properties:39-43` (`provider.master.key`) — **não reutilizar esta chave**: MFA terá `MFA_MASTER_KEY` própria |
| Cache Redis com TTL e chave por tenant | `TokenCacheServiceImpl.java:36-58` (`hive:auth:refresh:{tenantId}:{token}`) |
| Blacklist de JTI na rotação | `SecurityTokenServiceImpl.java:104` |
| Allowlist de rota com `AntPathMatcher` para access expirado | brain `atlas-cobranca/allowlist-access-expirado-refresh-logout` — padrão a aplicar em `/auth/mfa/*` se o `SecurityFilter` interferir |
| Cliente omite `Authorization` em rotas autenticadas por outra posse | brain `hive/frontend-logout-omit-authorization` e `refresh-bearer-expirado-securityfilter-before-permitall` |
| Erros semânticos sem payload de credencial | `auth.service.ts` (frontend) + `ControllerExceptionHandler.java:36-45` (backend) |

## 4. Lacunas a resolver na implementação

1. `MFA_MASTER_KEY` não existe no repo — definir property `mfa.master.key` com env `MFA_MASTER_KEY`, **sem fallback inseguro em produção**. Em dev, exigir valor explícito (o repo já Mostra esse padrão com `provider.master.key`).
2. `TokenCacheService` tem API de refresh; estender com operações de challenge (`saveChallenge/getChallenge/deleteChallenge/incrementAttempt`) ou criar `MfaChallengeCache` separado — decidir no PLAN.
3. Migration `V4__create_table_user_mfa.sql` em `backend/identity/dbupdate/src/main/resources/db/migration/identity_db/` (V2 e V3 existem).
4. Verificar na implementação se `DefaultCodeGenerator`/`DefaultCodeVerifier` compilam com Java 25 (usar `mvn -f backend/pom.xml test -q` como prova).

## 5. Fase 0 — resultado (validação de build, 2026-11-20)

Status: **aprovado**. Lib compila e gera/verifica código em Java 25. Nenhum domínio MFA criado nesta fase.

### Versões resolvidas

| Artefato | Versão declarada | Versão resolvida | Observação |
|---|---|---|---|
| `dev.samstevens.totp:totp` | 1.7.1 (`${totp.version}`) | 1.7.1 | compile em `hive-security-impl`; versão centralizada em `backend/dependencies/pom.xml` |
| `commons-codec:commons-codec` | 1.13 (POM da lib) | **1.21.0** | version managed pelo Spring Boot BOM 4.1.0 — mais novo que o declarado pela lib |
| `com.google.zxing:core` | 3.4.0 | 3.4.0 | transitiva compile |
| `com.google.zxing:javase` | 3.4.0 | 3.4.0 | transitiva compile; `core` omitido por duplicata |
| `commons-net:commons-net` | 3.6 (POM da lib, `<optional>true</optional>`) | **ausente** | não propagada ao consumidor; ver decisão abaixo |

Fonte das versões declaradas: POM publicado `~/.m2/repository/dev/samstevens/totp/totp/1.7.1/totp-1.7.1.pom` (mirror de https://mvnrepository.com/artifact/dev.samstevens.totp/totp, consultado 2026-11-20).

### Assinaturas reais confirmadas no jar (`javap`, 2026-11-20)

Comando: `javap -classpath ~/.m2/repository/dev/samstevens/totp/totp/1.7.1/totp-1.7.1.jar <classe>`;
contraste com javadoc oficial: https://javadoc.io/doc/dev.samstevens.totp/totp/1.7.1 (consultado 2026-11-20).

```
dev.samstevens.totp.secret.DefaultSecretGenerator
  public java.lang.String generate();

dev.samstevens.totp.time.SystemTimeProvider
  public long getTime();   // Instant.now().getEpochSecond() → SEGUNDOS

dev.samstevens.totp.code.DefaultCodeGenerator
  public java.lang.String generate(java.lang.String, long)
      throws dev.samstevens.totp.exceptions.CodeGenerationException;

dev.samstevens.totp.code.DefaultCodeVerifier
  public DefaultCodeVerifier(CodeGenerator, TimeProvider);
  public void setTimePeriod(int);                 // default 30
  public void setAllowedTimePeriodDiscrepancy(int); // default 1
  public boolean isValidCode(java.lang.String, java.lang.String);
```

**Correção crítica vs. README** (o README/Fase 0 sugeria `generate(secret)` de 1 arg): a 1.7.1 só expõe `generate(secret, timeStep)`. O bytecode de `generateHash` usa `time` diretamente como contador HMAC, sem divisão por período; `isValidCode` calcula `Math.floorDiv(getTime(), timePeriod)` antes de gerar. Logo, o chamador deve passar `Math.floorDiv(SystemTimeProvider.getTime(), 30)`, não epoch seconds crus. Passar epoch seconds gera código inválido (defeito detectado e corrigido no teste desta fase).

Outros detalhes de comportamento confirmados por bytecode:
- `CodeGenerationException extends Exception` — **checked**; assinatura do teste declara `throws`.
- `TimeProviderException extends RuntimeException` — unchecked.
- `DefaultCodeGenerator()` defaults: SHA1, 6 dígitos.
- `isValidCode` captura `CodeGenerationException` e retorna `false`; comparação é length-check + XOR em tempo constante; código de tamanho diferente retorna `false` sem exceção.

### Evidência de execução

| Comando | Exit code | Resultado |
|---|---|---|
| `mvn -f backend/pom.xml -pl security/impl -am -Dtest=TotpLibrarySmokeTest -Dsurefire.failIfNoSpecifiedTests=false test -q` | 0 | 2/2 testes do smoke passaram |
| `mvn -f backend/pom.xml test -q` | 0 | **425 testes, 0 falhas, 0 erros, 0 skips** (423 pré-existentes + 2 novos) |
| `mvn -f backend/pom.xml dependency:tree -pl security/impl -Dverbose` | 0 | ver tabela de versões; sem `commons-net` |
| `mvn -f backend/pom.xml dependency:tree -pl security/impl -Dincludes=commons-net:commons-net` | 0 | nenhuma linha `commons-net` — BUILD SUCCESS |

Teste: `backend/security/impl/src/test/java/br/com/systec/hive/security/impl/TotpLibrarySmokeTest.java`
- `when_generatedCodeIsVerified_then_valid` → `isValidCode(secret, code) == true`
- `when_differentCodeIsVerified_then_invalid` → `isValidCode(secret, wrongCode) == false` (wrongCode derivado do código gerado com 1 dígito trocado, sem log de secret/código).

### Decisão sobre `commons-net`

**Manter sem exclusão.** `commons-net:3.6` é `<optional>true</optional>` no POM da lib; dependências opcionais não são transitivas ao consumidor Maven, confirmado pela árvore filtrada (vazia). O adaptador da Fase 2 usará `SystemTimeProvider`, nunca `NtpTimeProvider`. Se uma futura atualização da lib tornar `commons-net` não-opcional, adicionar `<exclusions>` no adaptador — não agora (evita ruído no diff e não há risco presente). Atenção registrada: `commons-codec` sobe de 1.13 para 1.21.0 via BOM do Spring Boot, o que mitiga o risco de dependência antiga apontado na seção 2.

### Justificativa do módulo

Dependência declarada em `backend/security/impl` com `<scope>compile</scope>` porque é lá que a Fase 2 colocará `TotpAdapter` (implementação concreta atrás de `MfaPort`). `security/api` permanece sem dependência de biblioteca externa — apenas a porta de domínio. Versão gerenciada em `hive-dependencies` (`<totp.version>` + `dependencyManagement`), seguindo o padrão do repo: nenhum número de versão hardcoded no módulo.
