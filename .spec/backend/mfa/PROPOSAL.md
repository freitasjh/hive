# MFA TOTP no Hive — PROPOSAL

> Status: **planejamento (sem código)** · Data: 2026-11-20 · Branch: `feature/login-tela-mfa`
> Decisor: operador (João) · Escopo: backend Spring Boot + contrato de login

## 1. Decisões do operador (afirmadas)

1. **Fator**: TOTP (app autenticador: Google Authenticator / Authy), não e-mail, não WebAuthn.
2. **Fluxo**: desafio **após** a senha — credencial correta não emite token de imediato quando MFA está ativo; exige segundo passo com código de 6 dígitos.
3. **Upgrade do Spring**: **não atualizar** — manter Spring Boot 4.1.0 / Java 25. Upgrades futuros só com necessidade técnica concreta.

## 2. Contexto verificado no repo (fonte executável)

| Fato | Fonte |
|---|---|
| Login `POST /v1/auth/login` → `{userId, accessToken, type}` + cookie refresh | `backend/security/web/internal/v1/.../LoginControllerV1.java:33,44-60` |
| Refresh `POST /v1/auth/refresh-token` → `{accessToken}`, exige `X-Tenant-ID` | `LoginControllerV1.java:63-80`; `SecurityTokenServiceImpl.java:62-89` |
| Logout `POST /v1/auth/logout` já implementado (revoga refresh no Redis) | `LoginControllerV1.java:87-101`; `LoginServiceImpl.java:63-68` |
| Cookie `refreshToken` HttpOnly, `Path=/`, 2 dias | `CookieUtil.java:15-31` |
| Claim `tenant-id` no JWT | `SecurityTokenServiceImpl.java:42,189` |
| `SecurityFilter` valida Bearer em todo `/v1/**`, mesmo em rotas `permitAll` | `SecurityFilter.java:49`; `SecurityConfig.java:75` |
| Usuários em `public.users`, colunas `username/password/federal_id/tenant_id` | `backend/identity/impl/.../entity/UserEntity.java:21-39`; migration `V2__create_table_user.sql` |
| AES-256-GCM para segredos de provider (chave mestra em config) | `backend/agent/impl/.../converter/AesGcmAttributeConverter.java` + `application-dev.properties:39-43` |
| Nenhum código de MFA/TOTP/OTP no repo | `grep -rli 'totp\|mfa\|otp\|2fa' backend --include='*.java'` → vazio |
| Spring Security 7 tem MFA framework, mas **TOTP não é fator nativo** | deepsearch: docs.spring.io/spring-security/reference/servlet/authentication/mfa.html + spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7 (consultados 2026-11-19) |

## 3. Modelo proposto (resumo)

- **Desafio opaco no Redis** (`mfa:challenge:{challengeId}`), TTL 2 min, 5 tentativas, com `userId`, `tenantId`, `remoteIp`, `userAgent`. Evita JWT de desafio e permite rate limiting.
- **Secret TOTP por usuário**, cifrado com chave mestra **dedicada** (`MFA_MASTER_KEY`), em tabela própria `user_mfa` (V4 de `identity_db`), com `enabled`, `secret_ciphertext`, `created_at`, `confirmed_at`.
- **Recovery codes** (8 códigos de uso único, hash SHA-256, armazenados em `user_mfa_recovery_code`).
- **Endpoints** em `/v1/auth/mfa/*`: `setup` (gera secret + QR), `enable` (confirma código), `disable`, `verify` (segundo passo do login), `recovery` (usa código de recuperação).
- **Login em dois tempos**: sem MFA → 200 com token (contrato atual intacto); com MFA → 200 com `{mfaRequired: true, challengeId, expiresIn}` e **sem accessToken**. Nenhuma mudança no `LoginResponseDTO` existente — novo DTO de desafio.
- **Nunca** expor secret em claro fora do `setup` (uma única vez, em resposta autenticada); nunca logar secret/código/token.

## 4. Fora de escopo deste planejamento (e da implementação subsequente)

- WebAuthn/passkey, e-mail/SMS, MFA obrigatório para todos (adoção é opt-in por usuário).
- Tela de gerenciamento MFA no frontend (será outra entrega; aqui só o contrato HTTP).
- Upgrade do Spring Boot, mudança de `SecurityFilter`, mudança de contrato de login/refresh/logout já entregues.

## 5. Riscos principais

| Risco | Mitigação |
|---|---|
| `SecurityFilter` devolve 401 com Bearer revogado antes do controller | clientes de `/auth/mfa/*` devem usar `omitAuthorization` (padrão já adotado em refresh/logout) |
| Força bruta de código de 6 dígitos | 5 tentativas por desafio + bloqueio 15 min por usuário + expiração 2 min |
| Vazamento do secret | cifra AES-GCM com chave dedicada, env obrigatório em produção, nunca em log |
| Clock drift do app autenticador | `DefaultCodeVerifier` com discrepância ±1 período (30 s) |
| Biblioteca TOTP desatualizada (1.7.1, 2020) | isolar atrás de interface `TotpService`; avaliar alternativa mantida antes de codar (ver RESEARCH.md) |
| Replay do access token após MFA | blacklist do JTI no challenge consumido (padrão já usado na rotação, `SecurityTokenServiceImpl.java:104`) |
