# VERIFY — provider-vendor (modo `verify` sdd-compiler)

> Data: 2026-09-25 · SPEC: `.spec/backend/provider-vendor/SPEC.md` · TASKS: 15/15 Done · JIRA: 16/16 Done

## 1. Completude ✅
- [x] 15/15 TASKS.md checkboxes Done (INFRA-01/02, BE-01..08, FE-01..04, DOC-01 pendente? **NÃO** — DOC-01 ainda Todo; ver §4)
- [x] Zero TODOs/FIXMEs no código novo (grep limpo)
- [x] JIRA_BOARD 15/16 Done (PV-DOC-01 Todo)
- [x] Reviews: 3× APPROVED, zero 🔴

## 2. Corretude ✅
- [x] `mvn clean install` SUCCESS; IT 28/28; `npm run test` 114/114; `npm run build` SUCCESS
- [x] RF1-RF5 cobertos (vendor real, credenciais mascaradas, validate sem persistir, V9 preserva legados, spring-ai removido — grep zero)
- [x] RN1/RN2 (401 genérico, sem segredo em log/response — audit §4.5)
- [x] CA1 (OPENAI/GEMINI chamam provider real — factory por vendor + ITs), CA2 (GET sem ciphertext — CredentialIT), CA3 (harness <3min E2E)

## 3. Coerência ✅
- [x] ADR-009 refletido (enum vendor, LangChain4j único, credencial por tenant, V9 por `llm_model`)
- [x] Sem vazamento de camada (`provider/api` sem web compile; `SsrfUrlValidator` delega p/ `ProviderBaseUrlGuard` por boundary)
- [x] Catálogo FE espelha BE (review confirmou match exato)

## 4. Bloqueador de archive ⚠️
- **DOC-01 (Delta §4.2 + AGENTS.md + fechar ADR-009) ainda Todo.** Archive BLOQUEADO até DOC-01 Done (ressalva PM R1 — condiciona Archive, não correção).

## 5. Security Verify — Fase 4.5 (skill `security-review`, 14 itens)
- [x] Acesso server-side em todos os endpoints (`@HasPermission(AGENT_MANAGE)` nos 4 novos — verificado no código)
- [x] `@Valid` em todo DTO de escrita (upsert + validate verificados)
- [x] JPQL parametrizado (repos derivados; TRUNCATE só em teste com quoted names)
- [x] Senhas/segredos: AES-256-GCM, chave em env, zero hardcoded (grep `sk-/AIza` limpo fora de testes/validadores)
- [x] JWT inalterado (fluxo auth intacto, LoginIT verde)
- [x] TLS/prod config: sem mudança (fora de escopo)
- [x] Security headers: sem mudança (fora de escopo)
- [x] Zero `v-html` novo no FE (verificado no review)
- [x] Sem segredos em código versionado/logs/responses (audit: PUT/DELETE sem valor; GET mascarado; IT anti-exposição)
- [x] Throttle em `validate` (10/min/tenant + IT 429) — fecha gap Fase 1 🟡
- [x] Deps auditadas (BOM 1.19.0; sem CVE nova introduzida — 2 artifacts oficiais LangChain4j)
- [x] Actuator/Swagger: sem mudança
- [x] SSRF: allowlist + IP privado/metadata bloqueados + ITs — fecha gap Fase 1 🟠 (redirect residual = risco aceito documentado)
- [x] Mobile: N/A
**14/14 — verdict: pass** (com 1 risco aceito documentado)

## Veredito
- 3 dimensões: ✅✅✅ · Security: **pass** · **Archive: BLOQUEADO até DOC-01.**
