-- ADR-003/COT02: cota própria da key, distinta do plim.r do JWT.
-- Nome explícito key_*; sem DROP e sem repair(). V1/V2/V3 intactas.
ALTER TABLE public_api_client_key
    ADD COLUMN key_chat_requests_per_minute INT NOT NULL DEFAULT 100;

-- Keys já emitidas preservam o limite efetivo que vinham usando via plim.r do tenant.
UPDATE public_api_client_key k
SET key_chat_requests_per_minute = COALESCE(
        (SELECT q.chat_requests_per_minute
         FROM public_api_tenant_quota q
         WHERE q.tenant_id = k.tenant_id),
        100);

ALTER TABLE public_api_client_key
    ADD CONSTRAINT chk_public_api_client_key_chat_rpm
        CHECK (key_chat_requests_per_minute BETWEEN 1 AND 10000);
