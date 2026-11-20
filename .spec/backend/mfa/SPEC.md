# MFA TOTP — SPEC (contrato técnico)

> Status: planejado, **não implementado**. Base: PROPOSAL.md + RESEARCH.md.

## 1. Modelo de dados

### `identity_db.V4__create_table_user_mfa.sql`

```sql
CREATE TABLE user_mfa (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    tenant_id uuid NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    secret_ciphertext text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz
);

CREATE TABLE user_mfa_recovery_code (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES user_mfa(user_id) ON DELETE CASCADE,
    code_hash text NOT NULL,          -- SHA-256 do código (nunca o código em claro)
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_recovery_user ON user_mfa_recovery_code (user_id);
```

- Histórico por módulo: `identity_db` já usa `V2__create_table_user.sql` e `V3__add_unique_constraints_users.sql`.
- `secret_ciphertext`: AES-256-GCM com `MFA_MASTER_KEY` (chave dedicada, 32 bytes base64), padrão análogo a `AesGcmAttributeConverter`.

## 2. Domínio (módulo `security`)

| Componente | Responsabilidade |
|---|---|
| `MfaSecret` (VO, `security/api`) | `userId`, `tenantId`, `secretCiphertext`, `enabled`, `confirmedAt` |
| `MfaRecoveryCode` (VO) | `id`, `userId`, `codeHash`, `usedAt` |
| `MfaChallenge` (VO) | `id`, `userId`, `tenantId`, `remoteIp`, `userAgent`, `attempts`, `expiresAt` |
| `MfaPort` (interface) | `generateSecret()`, `qrData(...)`, `verify(secret, code)`, `newRecoveryCodes()` |
| `TotpAdapter` (`security/impl`) | implementa `MfaPort` com `dev.samstevens.totp:totp:1.7.1` isolado |
| `MfaService` | orquestra setup/enable/disable/verify/recovery; cifra/decifra secret; persiste via repositório |
| `MfaChallengeCache` | `save/get/increment/delete` no Redis, chave `hive:auth:mfa:{tenantId}:{challengeId}`, TTL 120 s, limite 5 tentativas |
| `MfaRepository` | CRUD de `user_mfa` + recovery codes (JPA em `identity/impl` ou `security/impl`, conforme fronteira existente — decidir no PLAN) |

## 3. Endpoints (`/v1/auth/mfa/*`, `permitAll`)

| Método | Rota | Autenticação | Body | Resposta |
|---|---|---|---|---|
| POST | `/setup` | Bearer (usuário logado) | — | `{secret, qrDataUri, recoveryCodes[]}` — secret e códigos **uma única vez** |
| POST | `/enable` | Bearer | `{code}` | 204; marca `enabled=true`, `confirmed_at=now()` |
| POST | `/disable` | Bearer | `{password}` | 204; remove secret e recovery codes |
| POST | `/verify` | sem Bearer (por challenge) | `{challengeId, code}` | 200 com `{accessToken, userId, type}` + `Set-Cookie refreshToken` (mesmo contrato do login) |
| POST | `/recovery` | sem Bearer (por challenge) | `{challengeId, recoveryCode}` | 200 com token; consome código (uso único) |

- `verify`/`recovery` usam `omitAuthorization` no cliente (padrão de refresh/logout) — o `SecurityFilter` valida Bearer antes do controller.
- `setup`/`enable`/`disable` exigem Bearer **válido** (usuário já autenticado controlando sua própria config).

## 4. Fluxo de login com desafio

```
POST /v1/auth/login {username, password}
 ├─ senha inválida → 401 INVALID_CREDENTIALS (inalterado)
 ├─ usuário sem MFA → 200 {userId, accessToken, type} + Set-Cookie (INALTERADO)
 └─ usuário com MFA ativo → 200 {
      mfaRequired: true,
      challengeId: <uuid opaco>,
      expiresIn: 120
    }   // SEM accessToken, SEM cookie de refresh

POST /v1/auth/mfa/verify {challengeId, code}
 ├─ challenge ausente/expirado → 401 MFA_CHALLENGE_EXPIRED
 ├─ 5 tentativas → 429 MFA_TOO_MANY_ATTEMPTS (+ delete challenge)
 ├─ código inválido → 401 MFA_INVALID_CODE (incrementa tentativas)
 └─ válido → 200 {accessToken, userId, type} + Set-Cookie refreshToken
             + blacklist do JTI do challenge (se houver)
```

### Regras do challenge
- Criado somente após senha correta e `user_mfa.enabled = true`.
- TTL 120 s; 5 tentativas; vinculado a `tenantId`, `remoteIp`, `userAgent` (mesma robustez do refresh, `SecurityTokenServiceImpl.java:76-84`).
- Consumido com sucesso → removido do Redis. Falha não remove até estourar tentativas.
- Nunca conter secret nem código.

## 5. Segurança

| Ameaça | Controle |
|---|---|
| Vazamento do secret | cifra AES-GCM (`MFA_MASTER_KEY`); exibido só no setup; nunca em log/URL |
| Força bruta do código | 5 tentativas + TTL 2 min + bloqueio 15 min por usuário após estouro |
| Replay do access | blacklist do JTI no challenge consumido |
| Cross-tenant | `tenantId` do challenge validado contra o JWT/cookie em todo endpoint |
| Phishing de setup | `setup` exige Bearer válido e reautenticação recente (senha ou token < 5 min) |
| Dependência antiga | `MfaPort` isola a lib; troca sem afetar domínio |
| Log de segredo | proibido; testes de auditoria de log (grep) no CI do módulo |

## 6. Contrato de erro (semântico, sem payload)

`MfaErrorCode`: `MFA_REQUIRED`, `MFA_CHALLENGE_EXPIRED`, `MFA_INVALID_CODE`, `MFA_TOO_MANY_ATTEMPTS`, `MFA_INVALID_RECOVERY`, `MFA_NOT_ENABLED`, `MFA_SETUP_FAILED`.
Mapeamento HTTP: 401 (expirado/código inválito/não habilitado), 429 (tentativas), 500 (setup falho). Nenhuma mensagem inclui secret, código ou token.

## 7. Critérios de aceite (para a implementação futura)

1. Usuário sem MFA: login continua 200 com token (zero regressão).
2. Usuário com MFA: senha correta → desafio; código correto → token idêntico ao fluxo atual; código errado 5× → 429; challenge expirado → 401.
3. Setup gera secret cifrado no banco + QR + 8 recovery codes; `enable` só.after confirmação de código.
4. Recovery code funciona uma vez e é invalidado.
5. `disable` remove secret e códigos; login volta ao fluxo de token direto.
6. Nenhum segredo/código/token em log, URL ou mensagem de erro.
7. `mvn -f backend/pom.xml test -q` exit 0; H3 real com app autenticador (ou gerador TOTP local) + backend + browser.
