# MFA TOTP — TASKS (microtasks por eixo)

> Derivado de SPEC.md + PLAN.md. Ordem: Fase 0 → 1 → 2 → 3 → (frontend) → 5.
> Cada task atômica: implementador → code-reviewer → commit na branch `feature/login-tela-mfa`.

## Backend — Fase 0 (decisão de lib)

- [ ] **MFA-01** validar `dev.samstevens.totp:totp:1.7.1` com Java 25: adicionar dependency em `backend/dependencies/pom.xml` + teste mínimo de `DefaultSecretGenerator` e `DefaultCodeVerifier`. Gate: `mvn -f backend/pom.xml test -q` exit 0.
- [ ] **MFA-02** deepsearch de advisories de `commons-net 3.6` (transitiva opcional) e decidir exclusão. Registrar em RESEARCH.md com URL+data.

## Backend — Fase 1 (domínio/persistência)

- [ ] **MFA-03** VOs `MfaSecret`, `MfaChallenge`, `MfaRecoveryCode` + enum `MfaErrorCode` em `security/api`.
- [ ] **MFA-04** migration `identity_db/V4__create_table_user_mfa.sql` (tabelas + índice conforme SPEC §1).
- [ ] **MFA-05** `MfaRepository` (JPA) + testes IT com Postgres real (padrão `WebIntegrationTest`).
- [ ] **MFA-06** `MfaChallengeCache` Redis (TTL 120 s, tentativas) + testes com Redis.
- [ ] **MFA-07** `MfaCryptoPort` + adaptador AES-GCM com `MFA_MASTER_KEY`; property `mfa.master.key`; boot fail-closed se ausente em prod; teste de cifra/decifra e auditoria de log.

## Backend — Fase 2 (serviço/endpoints)

- [ ] **MFA-08** `TotpAdapter` implementando `MfaPort` (secret, QR PNG/data URI, verify, recovery codes) — assinaturas conferidas no jar, não em memória.
- [ ] **MFA-09** `MfaService`: setup (gera+cifra+qr+8 recovery), enable (confirma código), disable (senha), verify (challenge), recovery (uso único).
- [ ] **MFA-10** `MfaControllerV1` `/v1/auth/mfa/{setup,enable,disable,verify,recovery}` + DTOs; Bearer em setup/enable/disable; `omitAuthorization` documentado para verify/recovery.
- [ ] **MFA-11** Rate limiting (5 tentativas, bloqueio 15 min) + blacklist JTI no sucesso; testes de 401/429/expirado.
- [ ] **MFA-12** Erros semânticos sem payload de segredo; auditoria de log (grep) nos testes do módulo.

## Backend — Fase 3 (integração ao login)

- [ ] **MFA-13** `LoginServiceImpl.login()`: ramo MFA → cria challenge, devolve `MfaChallengeResponseDTO`; usuário sem MFA intacto.
- [ ] **MFA-14** Teste de não-regressão do login atual (200 com token, cookie refresh, contrato `LoginResponseDTO` inalterado).
- [ ] **MFA-15** Suíte completa backend 0 falhas + code review sem 🔴.

## Frontend (entrega separada)

- [ ] **MFA-F1** detectar `mfaRequired` no login → `#mfa/verify` sem shell, `challengeId` em memória.
- [ ] **MFA-F2** formulário de código 6 dígitos (loading/erro/contador/expiração) + `omitAuthorization` + `credentials: 'include'`.
- [ ] **MFA-F3** tela de setup (QR + secret único + 8 recovery codes) e disable.

## Verificação final

- [ ] **MFA-V1** H3 real com gerador TOTP local (RFC 6238) cobrindo a matriz da SPEC §7.
- [ ] **MFA-V2** `npm test` + `mvn -f backend/pom.xml test -q` + `doctor` — tudo verde.
- [ ] **MFA-V3** registros no Brain + `workflow-state.json` + commit/push da branch.

## Dependências

```
MFA-01 ─→ MFA-08
MFA-02 ─→ MFA-01
MFA-03/04/05/06/07 ─→ MFA-09 ─→ MFA-10 ─→ MFA-11/12 ─→ MFA-13/14/15
MFA-15 ─→ MFA-F1/F2/F3 ─→ MFA-V1/V2/V3
```
