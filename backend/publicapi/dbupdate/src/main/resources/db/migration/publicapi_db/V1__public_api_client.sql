CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE public_api_client
(
    id                       UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at                TIMESTAMP WITHOUT TIME ZONE,
    update_at                TIMESTAMP WITHOUT TIME ZONE,
    tenant_id                UUID NOT NULL,

    client_id                VARCHAR(64)  NOT NULL,
    label                    VARCHAR(255) NOT NULL,
    secret_hash              VARCHAR(72)  NOT NULL,
    previous_secret_hash     VARCHAR(72),
    previous_secret_expires_at TIMESTAMP WITHOUT TIME ZONE,

    rate_limit_per_minute    INT NOT NULL DEFAULT 60,
    token_ttl_seconds        INT NOT NULL DEFAULT 600,

    max_messages             INT NOT NULL DEFAULT 200,
    max_message_chars        INT NOT NULL DEFAULT 32000,
    max_total_chars          INT NOT NULL DEFAULT 256000,

    revoked_at               TIMESTAMP WITHOUT TIME ZONE,
    last_used_at             TIMESTAMP WITHOUT TIME ZONE,
    expires_at               TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT pk_public_api_client PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_client_id UNIQUE (client_id),
    CONSTRAINT uk_public_api_client_tenant_label UNIQUE (tenant_id, label),
    CONSTRAINT chk_public_api_client_rate_limit  CHECK (rate_limit_per_minute >= 1),
    CONSTRAINT chk_public_api_client_token_ttl    CHECK (token_ttl_seconds BETWEEN 60 AND 3600),
    CONSTRAINT chk_public_api_client_max_messages CHECK (max_messages >= 1),
    CONSTRAINT chk_public_api_client_max_msg_chars CHECK (max_message_chars >= 1),
    CONSTRAINT chk_public_api_client_max_total_chars CHECK (max_total_chars >= max_message_chars),
    CONSTRAINT chk_public_api_client_previous_secret CHECK (
        (previous_secret_hash IS NULL AND previous_secret_expires_at IS NULL)
        OR (previous_secret_hash IS NOT NULL AND previous_secret_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_public_api_client_tenant ON public_api_client (tenant_id);
CREATE INDEX idx_public_api_client_revoked ON public_api_client (revoked_at) WHERE revoked_at IS NOT NULL;

CREATE TABLE public_api_client_grant
(
    id             UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at      TIMESTAMP WITHOUT TIME ZONE,
    update_at      TIMESTAMP WITHOUT TIME ZONE,
    tenant_id      UUID NOT NULL,

    client_ref     UUID NOT NULL,
    permission     VARCHAR(64) NOT NULL,

    CONSTRAINT pk_public_api_client_grant PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_grant UNIQUE (client_ref, permission),
    CONSTRAINT fk_public_api_client_grant_client FOREIGN KEY (client_ref)
        REFERENCES public_api_client (id) ON DELETE NO ACTION
);

CREATE INDEX idx_public_api_client_grant_client ON public_api_client_grant (client_ref);

CREATE TABLE public_api_client_prompt
(
    id                UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at         TIMESTAMP WITHOUT TIME ZONE,
    update_at         TIMESTAMP WITHOUT TIME ZONE,
    tenant_id         UUID NOT NULL,

    client_ref        UUID NOT NULL,
    prompt_code       VARCHAR(255) NOT NULL,
    pin_mode          VARCHAR(16) NOT NULL DEFAULT 'FOLLOW_ACTIVE',
    pinned_version    INT,

    CONSTRAINT pk_public_api_client_prompt PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_prompt UNIQUE (client_ref, prompt_code),
    CONSTRAINT fk_public_api_client_prompt_client FOREIGN KEY (client_ref)
        REFERENCES public_api_client (id) ON DELETE NO ACTION,
    CONSTRAINT chk_public_api_client_prompt_pin_mode
        CHECK (pin_mode IN ('FOLLOW_ACTIVE', 'PINNED')),
    CONSTRAINT chk_public_api_client_prompt_pin_pair CHECK (
        (pin_mode = 'PINNED'      AND pinned_version IS NOT NULL AND pinned_version >= 1)
        OR (pin_mode = 'FOLLOW_ACTIVE' AND pinned_version IS NULL)
    )
);

CREATE INDEX idx_public_api_client_prompt_client ON public_api_client_prompt (client_ref);
