-- BE-07: backfill legado agent.knowledge_bases TEXT[] -> knowledge_base + agent_knowledge_base.
-- Idempotente: ON CONFLICT DO NOTHING em KB e juncao.

INSERT INTO knowledge_base (tenant_id, name, slug, description, embedding_model, dimension, status)
SELECT DISTINCT
    a.tenant_id,
    kw AS name,
    lower(regexp_replace(kw, '[^a-zA-Z0-9]+', '-', 'g')) AS slug,
    'Backfill legado agent.knowledge_bases' AS description,
    'text-embedding-3-small' AS embedding_model,
    1536 AS dimension,
    'ACTIVE' AS status
FROM agent a
CROSS JOIN LATERAL unnest(a.knowledge_bases) AS kw
WHERE a.knowledge_bases IS NOT NULL
  AND a.tenant_id IS NOT NULL
ON CONFLICT (tenant_id, slug) DO NOTHING;

INSERT INTO agent_knowledge_base (tenant_id, agent_id, kb_id)
SELECT DISTINCT a.tenant_id, a.id, kb.id
FROM agent a
CROSS JOIN LATERAL unnest(a.knowledge_bases) AS kw
JOIN knowledge_base kb
  ON kb.tenant_id = a.tenant_id
 AND kb.slug = lower(regexp_replace(kw, '[^a-zA-Z0-9]+', '-', 'g'))
WHERE a.knowledge_bases IS NOT NULL
  AND a.tenant_id IS NOT NULL
ON CONFLICT (agent_id, kb_id) DO NOTHING;
