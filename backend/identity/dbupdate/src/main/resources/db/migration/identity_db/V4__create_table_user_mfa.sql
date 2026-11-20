-- MFA TOTP (Fase 1). Histórico por módulo: esta migration pertence a identity_db,
-- mesmo histórico de V2__create_table_user.sql e V3__add_unique_constraints_users.sql.
-- Nunca reescrever versões já aplicadas: use sempre uma versão nova (V5, V6, ...).

CREATE TABLE user_mfa
(
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    tenant_id        UUID NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT false,
    secret_ciphertext TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at     TIMESTAMPTZ
);

CREATE TABLE user_mfa_recovery_code
(
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES user_mfa (user_id) ON DELETE CASCADE,
    code_hash  TEXT NOT NULL,          -- SHA-256 do código (nunca o código em claro)
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_recovery_user ON user_mfa_recovery_code (user_id);
