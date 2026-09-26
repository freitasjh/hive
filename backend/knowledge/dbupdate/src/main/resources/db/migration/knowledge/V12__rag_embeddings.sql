CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE kb_embeddings_1536
(
    embedding_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    kb_id        UUID NOT NULL,
    tenant_id    UUID NOT NULL,
    doc_id       UUID NOT NULL,
    chunk_index  INT NOT NULL,
    embedding    vector(1536) NOT NULL,
    text         TEXT NOT NULL,
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT pk_kb_embeddings_1536 PRIMARY KEY (embedding_id),
    CONSTRAINT fk_kb_emb1536_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id) ON DELETE CASCADE
);

CREATE INDEX idx_kb_emb1536_tenant_kb ON kb_embeddings_1536 (tenant_id, kb_id);
CREATE INDEX idx_kb_emb1536_doc ON kb_embeddings_1536 (doc_id);
CREATE INDEX idx_kb_emb1536_vector ON kb_embeddings_1536 USING hnsw (embedding vector_cosine_ops);

CREATE TABLE kb_embeddings_768
(
    embedding_id UUID NOT NULL DEFAULT uuid_generate_v4(),
    kb_id        UUID NOT NULL,
    tenant_id    UUID NOT NULL,
    doc_id       UUID NOT NULL,
    chunk_index  INT NOT NULL,
    embedding    vector(768) NOT NULL,
    text         TEXT NOT NULL,
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT pk_kb_embeddings_768 PRIMARY KEY (embedding_id),
    CONSTRAINT fk_kb_emb768_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id) ON DELETE CASCADE
);

CREATE INDEX idx_kb_emb768_tenant_kb ON kb_embeddings_768 (tenant_id, kb_id);
CREATE INDEX idx_kb_emb768_doc ON kb_embeddings_768 (doc_id);
CREATE INDEX idx_kb_emb768_vector ON kb_embeddings_768 USING hnsw (embedding vector_cosine_ops);
