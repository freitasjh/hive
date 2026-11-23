---
prd_id: public-chat-api
title: Evolução da API pública do Assistant (chat-completion)
status: aprovado_humano
prd_status: archived
date: 2026-11-21
owner: architect (proposta) — aprovação: operador humano
---

# PRD — API pública do Assistant

> Artefato canônico da esteira (`.agents/rules/prd-adr-pipeline.md:9-16`). Substitui, para efeito de esteira, o ledger de candidatos em `ADR-INDEX.md`. Fontes de estudo: `EXPLORATION.md`, `CONSTRAINTS.md`, `DESIGN-DECISIONS.md`, `ARCHITECTURE.md`, `ARCHITECT-REVIEW.md`, `RESEARCH.md`.

## 1. Objetivo e problema

> ❓ Validação: as 4 frentes estão mapeadas em RF1–RF7 e nos 5 ADRs candidatos.

A superfície pública do assistant já funciona, mas cresceu por camadas: contrato compatível com OpenAI montado à mão (`Map<String,Object>`), DTO de request duplicado, credencial OAuth `client_credentials` provisionada por CLI, duas cadeias de segurança, cota em Redis com limite fixo por client e nenhuma especificação OpenAPI. Evoluir em quatro frentes: (1) contrato tipado + Swagger; (2) centralização com o chamador humano; (3) gestão de credenciais com UI admin; (4) cota por tenant + resiliência.

## 2. Escopo

> ❓ Validação: inclusão/exclusão revisadas pelo operador na assinatura de §16.

**Incluído:** DTOs tipados de request/response; spec OpenAPI groupada e validada; unificação da conversão de histórico e precedência do `SystemMessage`; preenchimento de `turnId`/`clientId` na trace; UI admin de `public_api_client*`; contador de cota por tenant com default 100 req/min; headers `X-RateLimit-*` em `429`.

**Fora do escopo:** crédito/budget consumível; token bucket com custo por token; migração para API keys `hk_`; aresta `publicapi/impl → agent/impl` (K3 — ADR 5 é estrutural e pode ser adiado); Ai Services/`ChatMemoryStore`; SEC-02; gate externo ERP; `application-dev.properties`.

## 3. Contexto e estado atual

Ver `EXPLORATION.md` §2 e `ARCHITECTURE.md` §1. Fatos verificados no código em 2026-11-21: `PublicChatServiceImpl.java:135` usa `checkClient(clientId, 60)`; `RedisQuotaGuard.java:114-116` já expõe `checkTenant`, sem chamador de produção; `PublicAuthControllerV1.java:81` usa `checkTokenEndpoint(rlClient, ip, 30, 120, 30)`; `V1__public_api_client.sql:28-31` fixa `client_id` globalmente único, `UNIQUE(tenant_id,label)` e `CHECK(rate_limit_per_minute >= 1)`; `PublicClientTokenIssuer.java:111-129` monta `plim = {m,c,t}` — **não existe chave `r` hoje**; `dependencies/pom.xml:33,126-128` declarava springdoc `2.8.14`; o upgrade para `3.2.0` foi concluído e medido na ADR-001 (commit `5177af7`, merge `d0d3890`).

## 4. Stakeholders

Operador (aprovação de §16 e das premissas de cota); times de backend e frontend (implementação por ADR); consumidor ERP (cliente da API); QA (evidência H0–H7).

## 5. Requisitos funcionais

> ❓ Validação: cada RF tem critério de aceite correspondente em §9.

| ID | Requisito | Como medir |
|---|---|---|
| RF1 | Contrato público tipado, com extensões Hive (`prompt_code`, `metadata.hive`) explícitas no schema | `ChatCompletionResponse`, `Choice`, `Message`, `Usage` e `HiveMetadata` presentes em `components.schemas`; IT de caracterização congela serialização idêntica ao baseline |
| RF2 | Spec OpenAPI em `/v3/api-docs`, group `public`, paths `/v1/public/**` e `components.schemas` não vazios | `GET /v3/api-docs` autenticado → 200 com os 2 paths e os schemas `ChatCompletionRequestDTO`, `ChatCompletionResponse`, `OpenAiError`; path canônico e alias testados; 401 anônimo |
| RF3 | Mesma ordem `SystemMessage → conversation` e mesmos números de `TokenUsage`/`FinishReason` nos dois chamadores | IT de caracterização antes/depois (ADR-002) |
| RF4 | `turnId` e `clientId` presentes em `ChatTrace` | Asserção no trace do turno público (ADR-002) |
| RF5 | Criar/listar/rotacionar/revogar/gerenciar grants e pin via UI admin; secret plaintext só no create/rotate | ITs CRUD + rotação com janela anterior + H3 375/1280 (ADR-003) |
| RF6 | Um contador por tenant, compartilhado entre clients, default 100 req/min, em `publicapi_db` | IT com dois clients do mesmo tenant competindo pelo contador (ADR-004) |
| RF7 | `429` com `Retry-After` e `X-RateLimit-{limit,remaining,reset}-requests` quando disponíveis | IT de cota asserando status e headers (ADR-004) |

## 6. Requisitos não-funcionais

> ❓ Validação: compatibilidade, segurança, resiliência e observabilidade cobertas por RFs e critérios de evidência.

- Compatibilidade: manter formato de erro `error.{message,type,param,code}` e o duplo `/v1` em `/hive/v1/public/v1/chat/completions`.
- Segurança: Basic preferido no token endpoint; nunca reutilizar chave JWT humana; `no-store`/`no-cache` na resposta de token; spec autenticada na chain humana por default.
- Resiliência: fail-open do guard preservado; Redis não vira dependência dura do turno.
- Observabilidade: suíte completa sem regressão; zero diff em `backend/security/**/Mfa*` e `identity/**/Mfa*`; H3 (375px/1280px, console/network limpos) quando houver UI.
- Testabilidade: ITs de caracterização e teste de dois clients do mesmo tenant competindo pelo contador.

## 7. Restrições e premissas confirmadas

> ❓ Validação: premissas de `client_id`, tenant e cota confirmadas pelo operador em 2026-11-21.

PICCO confirmado em 2026-11-21 (`CONSTRAINTS.md` §1): somente `.md` **na fase de estudo**; escrita em `.spec/prd/**`; não tocar MFA nem `workflow-state.json`; teto de 5 ADRs; toda recomendação de lib/versão exige URL + data. Marco histórico: a implementação da ADR-001 foi executada depois, por `backend-engineer` sob autorização do operador, dentro das regras da esteira. Premissas do operador (`CONSTRAINTS.md` §7): `client_id` globalmente único, tenant vem da linha, um tenant pode ter N clients; limite por tenant compartilhado, 100 req/min; origem em migration em `publicapi_db`; crédito fora; token endpoint mantém 30/120/30 separados.

## 8. Alternativas consideradas

Resumo em `DESIGN-DECISIONS.md`; comparativo com fontes e recomendação em `RESEARCH.md`. Decisões: contrato B (DTOs tipados, code-first com spec versionada); centralização A agora / B no ADR 5 / C fora; credenciais A (`client_credentials` + UI admin); cota A (sliding-window + `checkTenant` + headers), B e C rejeitadas.

## 9. Critérios de aceite

> ❓ Validação: critérios mensuráveis e verificáveis por comando/IT; nenhum critério subjetivo.

- `GET /v3/api-docs` (group `public`) → 200, paths `/v1/public/**` presentes, `components.schemas` com `ChatCompletionResponse`, `ChatCompletionRequestDTO`, `OpenAiError`.
- DTO duplicado em `publicapi/web/internal/v1` removido; contrato de erro inalterado no formato.
- IT de caracterização: ordem de mensagens e `prompt_tokens`/`total_tokens`/`finish_reason` idênticos antes/depois.
- `ChatTrace` com `turnId`/`clientId` preenchidos.
- Rotação com janela `previous_secret_hash`; revogação imediata; secret nunca em listagem; H3 verde.
- Migration V2 em `publicapi_db` com backfill de tenants existentes e coluna nominalmente específica de chat RPM.
- Dois clients do mesmo tenant: o segundo recebe `429` quando o somatório estoura; tenants diferentes não compartilham chave Redis.
- Suíte completa 0 falhas; `code-reviewer` sem 🔴; `brain_store` registrado.

## 10. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| springdoc 2.8.14 fora da matriz gera spec vazia — **RESOLVIDO na ADR-001** | upgrade para 3.2.0 aplicado; gate medido com 27 paths e 46 schemas; evidência em `ADRs/ADR-001-contrato-openapi/VERIFY.md` |
| Staleness do limite no JWT (TTL ≤ 3600 s) | aceito; estratégia de propagação por reemissão/revogação documentada na UI |
| Coluna genérica de cota trava crédito futuro | nome explícito `chat_requests_per_minute` na tabela de tenant |
| Aumento de latência por leitura de DB por turno | rejeitada; limite via claim |
| K12 (custo de ocupação) não resolvido | registrado como débito de produto |
| Mudança de exposição da spec quebre Scalar — **RESOLVIDO na ADR-001** | 401 anônimo e 200 com sessão humana testados em `/v3/**`, `/swagger-resources/**` e `/scalar/**` |

## 11. Impacto em módulos e dados

`publicapi/api` (DTOs), `publicapi/impl` (serviço de gestão, `PublicChatServiceImpl`, `PublicClientTokenIssuer`, `RedisQuotaGuard`), `publicapi/dbupdate` (migration V2), `publicapi/web/internal/v1` (remoção da duplicata + endpoints admin), `agent/api` (função pura de conversão), frontend `modules/hive/admin`. Grafo inalterado neste PRD (ADR 5 cuida de K3).

## 12. Estratégia de testes e evidência

Ver `ARCHITECTURE.md` §5 e `CONSTRAINTS.md` §6. Portão de conclusão H6: suíte 0 falhas · front test+build 0 erros · H3 quando aplicável · `code-reviewer` sem 🔴 · `brain_store` + `workflow-state.json` · portas livres.

## 13. Sequenciamento

Pré-condição springdoc → ADR 1 (contrato/OpenAPI) — **concluída: `merged`, PR #4, `d0d3890`, `VERIFY.md`** → ADR 2 (conversão/trace) → ADR 3 (UI admin) → ADR 4 (cota) → ADR 5 (fronteira de módulos, adiável). Teto de 5 ADRs respeitado.

## 14. Débitos registrados (fora do PRD)

K12, SEC-02, gate externo ERP, `application-dev.properties` sujo, crédito, Ai Services/`ChatMemoryStore`. Bugfix do `429` com `Retry-After` no token endpoint (Decisão 7 da ADR-001) — comportamento real é 400 `invalid_request`; correção destinada à ADR-004, não mesclada neste ciclo. Ver `ADR-INDEX.md` §"Fora deste PRD".

## 15. Fontes consultadas

<task>
Levantar, verificar e citar fontes externas que sustentam as decisões do PRD: RFC 6749 (client_credentials), contrato e erros OpenAI, rate limits e headers de cota, sliding-window em Redis, matriz de compatibilidade springdoc × Spring Boot e boas práticas OpenAPI. Toda afirmação de versão/API deve trazer URL e data da consulta.
</task>
<context>
PRD `public-chat-api`, aprovado em 2026-11-21 (§16, `prd_version: 2`). Estudo prévio em `EXPLORATION.md`, `CONSTRAINTS.md`, `DESIGN-DECISIONS.md`, `ARCHITECTURE.md`, `RESEARCH.md` e `ARCHITECT-REVIEW.md`. Stack: Spring Boot 4.1.0, Java 25 preview, LangChain4j 1.19.0, springdoc 3.2.0 (upgrade decidido na ADR-001).
</context>

Todas em 2026-11-21. `https://www.rfc-editor.org/rfc/rfc6749.html` · `https://platform.openai.com/docs/api-reference/chat/create` · `https://developers.openai.com/api/docs/guides/error-codes` · `https://developers.openai.com/api/docs/guides/rate-limits` · `https://redis.io/learn/howtos/ratelimiting` · `https://springdoc.org/faq.html` · `https://springdoc.org/` · `https://github.com/springdoc/springdoc-openapi/releases` · `https://learn.openapis.org/best-practices.html`. Verificação adicional de versão em `RESEARCH.md` §5.

## 16. Aprovação humana

> ❓ Validação: veredito, data e `prd_version` presentes; assinatura é ato humano registrado em 2026-11-21.

Veredito: APROVADO

**Status: APPROVED — aprovação humana registrada em 2026-11-21.**

- [X] Aprovado por: João Henrique
- [X] Data: 2026-11-21
- [X] Escopo aprovado: 4 frentes / 5 ADRs candidatos / teto respeitado
- [X] Premissas de cota confirmadas (tenant compartilhado, 100 req/min, crédito fora)
- [X] `prd_version: 2` — toda linha `in-progress`/`merged` exige `unit: adr` (`SPEC.md` + `TASKS.md`; `VERIFY.md` após merge), conforme `.agents/rules/prd-adr-pipeline.md:28-33`.

**Trava levantada:** campos marcados pelo operador em 2026-11-21; `prd_version: 2`. Com `unit: adr`, linhas `in-progress`/`merged` exigem `SPEC.md` + `TASKS.md` (e `VERIFY.md` após merge) — `.agents/rules/prd-adr-pipeline.md:28-33`.

**Marcos concluídos (2026-11-21):** índice convertido ao formato canônico; ADR-001 aberta, implementada, revisada e mesclada (PR #4, merge `d0d3890`); `VERIFY.md` publicado em `5658264`. Próximo: ADR-002.

## Arquivamento

- **Data:** 2026-11-23.
- **Duração:** 2026-11-21 → 2026-11-23.
- **Traceability:** 100% — 23/23 RFs, 27/27 tasks, 0 órfãos (`TRACEABILITY.md`).
- **Teto de 5 ADRs:** atendido — ADR-001..005 todas `merged`, nenhuma ADR fora do índice.
- **Artefatos entregues:** `PRD.md`, `RESEARCH.md`, `EXPLORATION.md`, `CONSTRAINTS.md`, `DESIGN-DECISIONS.md`, `ARCHITECTURE.md`, `ARCHITECT-REVIEW.md`, `ADR-INDEX.md`, `TRACEABILITY.md`, `CODE-REVIEW.md` e, por ADR, `ADR.md`/`SPEC.md`/`TASKS.md`/`VERIFY.md` (com `CODE-REVIEW.md` nas ADRs 002–005).
- **Código entregues:** spec OpenAPI tipada e gate autenticado; paridade de conversão e trace atribuível; admin de credenciais com secret one-shot; cota por tenant com `429` no token; portas de política de turno em `assistant/api` sem aresta `impl → impl`.
- **PRs/fechamentos:** ADR-001 merge `d0d3890` (PR #4), fechamento `5658264`; ADR-002 merge `c71081e` (PR #5), fechamento `293d778`; ADR-003 merge `431150e` (PR #6), fechamento `289172b`; ADR-004 merge `37f0cab` (PR #7), fechamento `948fc42`; ADR-005 merge `58c8c9d` (PR #8), fechamento `0699b96`.
