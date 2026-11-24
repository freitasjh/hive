-- ADR-002/KEY2-02: API key complementar (Bearer sk-), separada do client_secret.
-- Nomes explícitos key_*; sem DROP e sem repair(). V3 é a próxima livre após V1/V2.
CREATE TABLE public_api_client_key
(
    id                       UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at                TIMESTAMP WITHOUT TIME ZONE,
    update_at                TIMESTAMP WITHOUT TIME ZONE,
    tenant_id                UUID NOT NULL,
    client_ref               UUID NOT NULL,

    key_id                   VARCHAR(64)  NOT NULL,
    key_hash                 VARCHAR(72)  NOT NULL,
    previous_key_id          VARCHAR(64),
    previous_key_hash        VARCHAR(72),
    previous_key_expires_at  TIMESTAMP WITHOUT TIME ZONE,
    revoked_at               TIMESTAMP WITHOUT TIME ZONE,
    expires_at               TIMESTAMP WITHOUT TIME ZONE,
    last_used_at             TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT pk_public_api_client_key PRIMARY KEY (id),
    CONSTRAINT uk_public_api_client_key_key_id UNIQUE (key_id),
    CONSTRAINT fk_public_api_client_key_client FOREIGN KEY (client_ref)
        REFERENCES public_api_client (id) ON DELETE NO ACTION,
    CONSTRAINT chk_public_api_client_key_previous CHECK (
        (previous_key_hash IS NULL AND previous_key_expires_at IS NULL)
        OR (previous_key_hash IS NOT NULL AND previous_key_expires_at IS NOT NULL)
    )
);

CREATE INDEX idx_public_api_client_key_previous_id ON public_api_client_key (previous_key_id);
CREATE INDEX idx_public_api_client_key_tenant ON public_api_client_key (tenant_id);
CREATE INDEX idx_public_api_client_key_client ON public_api_client_key (client_ref);
