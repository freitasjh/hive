CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- BE-07: migra agentes legados (provider framework -> vendor real).
-- Mapeamento pela coluna llm_model (nomes do enum legado de modelos).
-- provider_type legado (enum legado de frameworks) e substituido pelo vendor derivado.
-- Idempotente: o WHERE so alcança valores legados; re-execucao e no-op.
-- Linhas com llm_model NULL sao preservadas intactas (decisao humana pendente).

UPDATE agent
SET provider_type = CASE llm_model
                        WHEN 'OLLHAMA' THEN 'OLLAMA'
                        WHEN 'OPENAI' THEN 'OPENAI'
                        WHEN 'GEMINI' THEN 'GEMINI'
                        WHEN 'OPENCODE' THEN 'OPENAI_COMPATIBLE'
                        ELSE provider_type
    END,
    llm_model = CASE llm_model
                    WHEN 'OLLHAMA' THEN 'llama3.2'
                    WHEN 'OPENAI' THEN 'gpt-4'
                    WHEN 'GEMINI' THEN 'gemini-1.5-pro'
                    WHEN 'OPENCODE' THEN 'mimo-v2.5'
                    ELSE llm_model
    END
WHERE llm_model IN ('OLLHAMA', 'OPENAI', 'GEMINI', 'OPENCODE');
