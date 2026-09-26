CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE knowledge_base
(
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at       TIMESTAMP WITHOUT TIME ZONE,
    update_at       TIMESTAMP WITHOUT TIME ZONE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(255) NOT NULL DEFAULT 'text-embedding-3-small',
    dimension       SMALLINT NOT NULL DEFAULT 1536,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT pk_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uq_knowledge_base_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_knowledge_base_tenant ON knowledge_base (tenant_id);

CREATE TABLE kb_document
(
    id          UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at   TIMESTAMP WITHOUT TIME ZONE,
    update_at   TIMESTAMP WITHOUT TIME ZONE,
    tenant_id   UUID NOT NULL,
    kb_id       UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_uri  VARCHAR(2048),
    file_name   VARCHAR(1024),
    sha256      VARCHAR(64),
    status      VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error       TEXT,

    CONSTRAINT pk_kb_document PRIMARY KEY (id),
    CONSTRAINT fk_kb_document_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id) ON DELETE CASCADE,
    CONSTRAINT uq_kb_document_tenant_sha UNIQUE (tenant_id, sha256)
);

CREATE INDEX idx_kb_document_kb ON kb_document (kb_id);
CREATE INDEX idx_kb_document_tenant ON kb_document (tenant_id);
CREATE INDEX idx_kb_document_status ON kb_document (status);

CREATE TABLE agent_knowledge_base
(
    id        UUID NOT NULL DEFAULT uuid_generate_v4(),
    create_at TIMESTAMP WITHOUT TIME ZONE,
    update_at TIMESTAMP WITHOUT TIME ZONE,
    tenant_id UUID NOT NULL,
    agent_id  UUID NOT NULL,
    kb_id     UUID NOT NULL,

    CONSTRAINT pk_agent_knowledge_base PRIMARY KEY (id),
    CONSTRAINT fk_agent_kb_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id) ON DELETE CASCADE,
    CONSTRAINT uq_agent_knowledge_base UNIQUE (agent_id, kb_id)
);

CREATE INDEX idx_agent_kb_agent ON agent_knowledge_base (agent_id);
CREATE INDEX idx_agent_kb_kb ON agent_knowledge_base (kb_id);
CREATE INDEX idx_agent_kb_tenant ON agent_knowledge_base (tenant_id);
