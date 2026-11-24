---
prd_id: public-api-key-complementar
title: API key complementar ligada ao client da API pública
status: aprovado_humano
date: 2026-11-23
owner: orquestrador (proposta) — aprovação: operador humano
---

# PRD — API key complementar da API pública

> Novo PRD. O PRD anterior e as ADRs 001–005 estão arquivados em `.spec/archive/20261123-prd-public-chat-api/` e **não serão editados**. Este documento propõe apenas uma superfície complementar de autenticação.

## 1. Objetivo e problema

> ❓ Validação: o problema é onboarding/compatibilidade de SDKs, não falha de segurança do modelo atual.

Integradores que vêm de OpenAI esperam enviar uma chave estática em `Authorization: Bearer sk-...`. Hoje o Hive exige `client_id` + `client_secret` → `POST /v1/public/auth/token` → JWT de curta duração (`aud=hive.public`). A troca é simples, mas cria atrito de onboarding e exige adaptação em SDKs. `x-goog-api-key` foi avaliado no RESEARCH e **ficou fora do escopo** por decisão do operador.

Objetivo: estudar e planejar uma **API key complementar**, ligada ao mesmo `client_id`/`tenant_id`, mantendo o `client_credentials` intacto. A key é apenas outra forma de apresentar a mesma identidade, com rotação e revogação imediata.

## 2. Escopo

> ❓ Validação: inclusão/exclusão serão revisadas na assinatura do §16.

**Incluído:** formato único `Authorization: Bearer sk-...`, emissão one-shot com hash verificável, vínculo ao client/tenant existentes, rotação e revogação, **cota própria da key com telemetria preservada**, contrato OpenAPI e documentação para integradores.

**Fora do escopo por decisão do operador:** `x-goog-api-key`, aceitação de ambos os headers e precedência entre eles.

**Fora do escopo:** substituir `client_credentials`; alterar o JWT atual; mudar cota por tenant, limites `m/c/t`, `turnId`/`clientId`, UI admin da ADR-003 ou ADRs arquivadas; crédito/budget; migração de clients existentes sem opt-in; código, migrations e config nesta fase.

## 3. Contexto e estado atual

- `PublicClientTokenIssuer.java:114-139` — claims `permissions`, `pcode`, `plock`, `plim` (com `r`), `tenant-id`, `aud=hive.public`, expiração por client.
- `PublicApiClientAccessWriter.java:23` — único grant útil: `PUBLIC_CHAT_COMPLETE`.
- `SecurityConfig.java` / `PublicApiSecurityFilter.java` — chain M2M em `/v1/public/**`; filtro valida JWT e abre `TenantContext`.
- `RedisQuotaGuard` — cota por tenant com `plim.r` e fail-open; chave `hive:public:quota-tenant:{tenantId}`.
- PRD anterior: 5 ADRs merged, 100% de traceability, arquivado em 2026-11-23.

## 4. Stakeholders

Operador (aprovação do §16); `product-manager` (valor de produto); `architect` e `security-engineer` (parecer); times backend/frontend; QA (H3/H6); integradores ERP.

## 5. Requisitos funcionais

> ❓ Validação: todo RF tem critério mensurável em §9.

| ID | Requisito | Como medir |
|---|---|---|
| RF1 | Aceitar credencial em `Authorization: Bearer sk-...` sem quebrar o JWT atual | IT: 200 no chat com key válida e com JWT válido; OpenAPI documenta os dois esquemas |
| RF2 | Cota própria da key, distinta do `plim.r` do JWT | IT: key com limite X e JWT com limite Y; ambos aplicados conforme credencial usada |
| RF3 | Key ligada ao mesmo `client_id`/`tenant_id` | IT: claim `tenant-id` e `permissions` idênticos aos do client; cross-tenant 404/401 |
| RF4 | Emissão one-shot, hash verificável, sem recuperação | IT: plaintext só na criação/rotação; listagem/get sem plaintext; hash BCrypt ou equivalente verificado |
| RF5 | Rotação com janela e revogação imediata | IT: key antiga válida na janela, inválida depois; revogação gera 401 imediato |
| RF6 | Telemetria preservada mesmo com cota própria | IT: `turnId`/`clientId` e `X-RateLimit-*` refletem o limite da credencial usada; key e JWT não compartilham `plim.r` |

## 6. Requisitos não-funcionais

> ❓ Validação: segurança, compatibilidade e observabilidade cobertas por RF e critério.

- **Segurança:** nunca logar plaintext; hash verificável; revogação imediata; tratar vazamento como incidente.
- **Compatibilidade:** `client_credentials` e envelope OpenAI intactos; erros `application/problem+json` e OpenAI preservados.
- **Deepsearch:** toda afirmação sobre API de terceiro com URL + data da consulta.
- **Qualidade:** suíte 0 falhas, ITs reais, `code-reviewer` sem 🔴; H3 se houver UI.
- **Rastreabilidade:** cada RF → ADR → tarefa → código/evidência.

## 7. Restrições e premissas confirmadas

> ❓ Validação: invariantes confirmados pelo operador em 2026-11-23.

Invariantes: não quebrar `client_credentials`; mesma identidade tenant/client; rotação + revogação imediata; **a key pode ter cota própria, mas `turnId`/`clientId` e os headers de rate limit são preservados**. PICCO confirmado; somente `.md` nesta fase; novo PRD em `.spec/prd/public-api-key-complementar/`; nada em `.spec/archive/**`. Formato único: `Bearer sk-...`; `x-goog-api-key` avaliado no RESEARCH e descartado do escopo.

## 8. Alternativas consideradas

Resumo em `RESEARCH.md`. Alternativa escolhida: (A) `Bearer sk-...`. Alternativas avaliadas e rejeitadas: (B) `x-goog-api-key`; (C) ambos — fora do escopo por decisão do operador; (D) manter só OAuth — baseline.

## 9. Critérios de aceite

> ❓ Validação: critérios executáveis, verificáveis por comando/IT.

- PRD com 16 seções e §16 assinado antes de qualquer ADR.
- RESEARCH com ≥2 alternativas, fontes oficiais (URL + data) e recomendação única.
- ADR-INDEX com candidatos 1:1 com futuros diretórios, sem criar `ADRs/**` antes do §16.
- ITs de key válida, cross-tenant, one-shot, rotação, revogação, cota própria da key e telemetria preservada (`turnId`/`clientId`).
- Suíte 0 falhas; build 0; H3/H6 verdes na implementação futura.

## 10. Riscos e mitigações

- Dado que uma key estática pode vazar, quando for usada em client M2M, então rotação e revogação imediata devem ser obrigatórias, com janela explícita.
- Dado que dois formatos de header aumentam superfície, quando o RESEARCH não provar ganho, então adotar um único formato recomendado.
- Dado que a key pode divergir do JWT, quando os claims diferirem, então falhar fechado e auditar.

## 11. Impacto em módulos e dados

Previsto: `publicapi/api` (DTOs), `publicapi/impl` (emissor/validador de key), `publicapi/web` (endpoints admin), `security/impl` (filtro da chain M2M), `publicapi/dbupdate` (hash de key), frontend admin e OpenAPI. Nenhuma migration nesta fase de estudo.

## 12. Estratégia de testes e evidência

H1/H2 antes de implementar; ITs com Postgres/Testcontainers; caracterização de claims e cota; H3 se houver UI; harness report por ADR; `brain_store` ao fechar cada ciclo.

## 13. Sequenciamento

PRD (§16) → RESEARCH → ADR-INDEX → ADR 1 (formato Bearer + contrato OpenAPI) → ADR 2 (emissão/hash + rotação/revogação) → ADR 3 (cota própria + telemetria). Teto de 5 ADRs; 3 candidatos. Nenhum ADR nasce antes da assinatura.

## 14. Débitos registrados (fora do PRD)

K12, SEC-02, crédito/budget, orfão `chat-model-override`, bugfix de grants por checkbox (cancelado), bugfix de débitos 1–3 (branch aguardando merge).

## 15. Fontes consultadas

- OpenAI — API Overview/Authentication — https://platform.openai.com/docs/api-reference/authentication — 2026-11-23.
- Gemini — Using API keys — https://ai.google.dev/gemini-api/docs/api-key — 2026-11-23.
- RFC 6749 — https://www.rfc-editor.org/rfc/rfc6749.html — 2026-11-21.
- RFC 9700 — https://www.rfc-editor.org/rfc/rfc9700.html — 2026-11-23.

<task>
Levantar fontes oficiais sobre formatos de API key e credenciais ligadas a identidade, comparar com o modelo atual do Hive e recomendar uma superfície complementar sem quebrar client_credentials.
</task>
<context>
PRD `public-api-key-complementar`, proposto em 2026-11-23. Modelo atual: OAuth client_credentials com JWT de curta duração e claims de tenant, escopo e cota. Stack Spring Boot 4.1.0/Java 25 preview. Invariantes fixados pelo operador: não quebrar OAuth; mesma identidade tenant/client; rotação e revogação imediata; key com cota própria e telemetria preservada. Formato único: Bearer sk-....
</context>

## 16. Aprovação humana

> ❓ Validação: veredito, data e ausência de aprovação forjada conferidos.

Veredito: APROVADO — aprovação humana registrada em 2026-11-23.

**Status: APPROVED — aprovação humana registrada em 2026-11-23.**

Data: 2026-11-23.

- [X] Aprovado por: João Henrique Freitas
- [X] Data: 2026-11-23
- [X] Escopo aprovado: 3 ADRs candidatos / invariantes confirmados / teto respeitado
- [X] `prd_version: 2` — ADRs com `unit: adr` (`SPEC.md` + `TASKS.md`; `VERIFY.md` após merge), conforme `.agents/rules/prd-adr-pipeline.md:28-33`.

**Trava:** nenhum `ADRs/**` deve nascer antes da assinatura deste §16. Após aprovação: converter `ADR-INDEX.md` em índice canônico e abrir o ADR 1.
