-- ADR-004/QT01: cota de chat por tenant compartilhada.
-- A coluna public_api_client.rate_limit_per_minute permanece (obsoleta no caminho de chat), sem DROP.
CREATE TABLE public_api_tenant_quota
(
    tenant_id                UUID NOT NULL,
    chat_requests_per_minute INT  NOT NULL DEFAULT 100,
    create_at                TIMESTAMP WITHOUT TIME ZONE,
    update_at                TIMESTAMP WITHOUT TIME ZONE,

    CONSTRAINT pk_public_api_tenant_quota PRIMARY KEY (tenant_id),
    CONSTRAINT chk_public_api_tenant_quota_chat_requests
        CHECK (chat_requests_per_minute BETWEEN 1 AND 10000)
);

-- Backfill: uma linha por tenant distinto que já tem client público, com o default 100.
INSERT INTO public_api_tenant_quota (tenant_id, chat_requests_per_minute, create_at, update_at)
SELECT DISTINCT tenant_id, 100, NOW(), NOW()
FROM public_api_client
ON CONFLICT (tenant_id) DO NOTHING;
