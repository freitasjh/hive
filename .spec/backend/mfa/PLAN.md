# MFA TOTP — PLAN (fases de implementação)

> Planejado em 2026-11-20. Nenhuma linha de código implementada ainda.
> Spring Boot **4.1.0** / Java **25** — sem upgrade (decisão do operador).

## Fase 0 — Decisão de biblioteca (HITL antes de codar)

- [ ] Validar `dev.samstevens.totp:totp:1.7.1` no build Java 25 (compila + 1 teste de geração/verificação).
- [ ] Checar advisories na versão transitiva `commons-net 3.6` (o `SystemTimeProvider` não usa NTP — considerar excluir o dependência opcional).
- [ ] Alternativa se reprovado: `com.warrenstrange:googleauth` (deepsearch próprio, URL+data) — trocar só o `TotpAdapter`.

## Fase 1 — Domínio e persistência (backend/security + identity)

- [ ] VO `MfaSecret`, `MfaChallenge`, `MfaRecoveryCode` + `MfaPort` em `security/api`.
- [ ] Migration `V4__create_table_user_mfa.sql` + índice (SPEC §1).
- [ ] `MfaRepository` (JPA) + `MfaChallengeCache` (Redis, padrão `TokenCacheServiceImpl`).
- [ ] Cifra AES-GCM com `MFA_MASTER_KEY` (property `mfa.master.key`, env obrigatório em prod; dev com valor explícito documentado).
- [ ] Testes de repositório/cifra com Postgres real + Redis (Testcontainers, padrão IT do repo).

## Fase 2 — Serviço e endpoints de gerenciamento

- [ ] `TotpAdapter` (lib isolada atrás de `MfaPort`).
- [ ] `MfaService`: setup/enable/disable/verify/recovery + rate limiting + blacklist JTI.
- [ ] `MfaControllerV1` em `/v1/auth/mfa/*` com DTOs (`MfaSetupResponseDTO`, `MfaEnableDTO`, `MfaVerifyDTO`, `MfaChallengeResponseDTO`, `MfaTokenResponseDTO`).
- [ ] `SecurityConfig`: manter `/v1/auth/**` permitAll; tratar `SecurityFilter` com allowlist se necessário (padrão brain atlas-cobranca).
- [ ] Testes MockMvc (matriz de sucesso/erro/429/expirado) + testes de service com mocks + log auditado.

## Fase 3 — Integração ao login (sem quebrar o atual)

- [ ] `LoginServiceImpl.login()`: após senha válida, checar `user_mfa.enabled` → gerar challenge em vez de token.
- [ ] `LoginResponseDTO` existente **inalterado**; novo `MfaChallengeResponseDTO` para o ramo MFA.
- [ ] Garantir zero regressão: usuário sem MFA recebe exatamente o contrato atual (teste de não-regressão obrigatório).
- [ ] Blacklist do JTI ao consumir challenge (reuso de `tokenCacheService.addToBlacklist`).

## Fase 4 — Frontend (entrega separada, após backend)

- [ ] Tela `#mfa/verify` (código de 6 dígitos) fora do shell, com contador de tentativas e expiração.
- [ ] `auth.service.ts`: detectar `mfaRequired` na resposta de login → navegar para `#mfa/verify` com `challengeId` em memória (não persistir challenge).
- [ ] `verify`/`recovery` com `omitAuthorization: true` (padrão refresh/logout) e `credentials: 'include'`.
- [ ] Tela de setup (QR + secret + recovery codes) em configurações do usuário.

## Fase 5 — Verificação (H6)

- [ ] Suíte raiz `npm test` (frontend) + `mvn -f backend/pom.xml test -q` — 0 falhas.
- [ ] H3 real: backend + browser + gerador TOTP local (ex.: script Python `hmac` conforme RFC 6238), cobrindo: login sem MFA (regressão), desafio, código válido, código inválido, 5ª tentativa, expiração, recovery, disable.
- [ ] `doctor` do harness sem WARN (pin G1 já fechado).
- [ ] Code review sem 🔴 em cada batch; commits atômicos por fase na branch `feature/login-tela-mfa`.

## Riscos / dependências entre fases

| Risco | Fase | Mitigação |
|---|---|---|
| Lib 1.7.1 malfunciona em Java 25 | 0 | prova de compilação antes de qualquer domínio |
| Chave `MFA_MASTER_KEY` ausente em produção | 1 | fail-closed: boot aborta se env não definido |
| `SecurityFilter` bloqueia `/auth/mfa/verify` | 2 | allowlist AntPathMatcher + `omitAuthorization` no cliente |
| Regressão no login atual | 3 | teste de não-regressão é gate da Fase 3 |
| Clock drift do app autenticador | 2 | discrepância ±1 período no `DefaultCodeVerifier` |
| Secret vazado em log | 2/5 | grep de auditoria + teste que falha se aparecer |

## Fora de escopo

WebAuthn, MFA obrigatório global, notificações por e-mail/SMS, upgrade do Spring, alteração dos contratos de login/refresh/logout já entregues.
