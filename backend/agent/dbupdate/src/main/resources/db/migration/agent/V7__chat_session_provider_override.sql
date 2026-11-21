-- ============================================================================
-- V7 — escolha de provider/modelo por conversa.
--
-- POR QUE V7
-- ───────────────────────────────────────────────────────────────────────────────
-- A série do módulo `agent` vai em V2 (prompt_template), V3 (provider_credential),
-- V4 (chat_session), V5 (chat_message) e V6 (seed do prompt). V7 é a próxima
-- versão livre. A V4 de `identity_db` pertence a outro histórico Flyway e não
-- ocupa esta numeração.
--
-- O QUE MUDA
-- ───────────────────────────────────────────────────────────────────────────────
-- `chat_session` passa a guardar o par (provider, model) escolhido para a
-- CONVERSA. NULL em ambos significa "usar o default do tenant", que continua
-- sendo a ÚNICA linha de `tenant_llm_config` — este módulo não a escreve, não a
-- lê aqui e não cria FK cross-module.
--
-- Os tipos repetem os de `tenant_llm_config` (tenant_db V3): VARCHAR(32) e
-- VARCHAR(255). Não há NOT NULL, não há CHECK e não há backfill: sessões antigas
-- ficam com NULL e passam a usar o default exatamente como antes. Preencher
-- `provider` sem `model` (ou o contrário) é erro de entrada, recusado no
-- domínio antes da escrita; o banco não precisa de constraint para isso.
--
-- FONTE DA DECISÃO (deepsearch 2026-11-20)
-- ───────────────────────────────────────────────────────────────────────────────
-- https://ui.shadcn.com/docs/ai-elements/base/model-selector
-- A doc oficial do Model Selector manda o modelo no body da request
-- (`sendMessage({ text }, { body: { model, effort } })`). O backend precisa
-- aceitar `provider` e `model` no payload do turno e na criação da sessão;
-- sem isso a escolha da UI nunca chega à resolução do provider.
-- ============================================================================

ALTER TABLE chat_session
    ADD COLUMN provider VARCHAR(32),
    ADD COLUMN model    VARCHAR(255);
