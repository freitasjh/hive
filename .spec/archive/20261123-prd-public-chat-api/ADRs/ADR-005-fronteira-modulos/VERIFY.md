# VERIFY — ADR-005: Fronteira de módulos (K3)

| Campo | Valor |
|---|---|
| **PRD** | `public-chat-api` (`prd_version: 2`, §16 aprovado em 2026-11-21) |
| **ADR** | `ADR-005` — `ADRs/ADR-005-fronteira-modulos/` |
| **Branch** | `adr/public-chat-api/05-fronteira-modulos` |
| **Abertura da ADR** | `0e59bda874bc4430c364248a25bdc0f646da5d9e` |
| **Commit de implementação** | `871fef453900db8a960aa937e20e56006fbeacd5` |
| **Merge** | `58c8c9dede599b6d156642f87f274f862ffc1c84` — PR #8 em `origin/main` (2026-11-23) |
| **Data verificação** | 2026-11-23 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-chat-api-adr-005.json` (`h6.status: PASS`, `mergeVerification: MERGE_CONFIRMADO`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `CODE-REVIEW.md` neste diretório |

---

## 1. Completude

MOD01..MOD05 concluídas. 14 arquivos no commit de implementação (8 código + 3 documentação corrigida + CODE-REVIEW + harness + estado), nenhum alheio.

| Tarefa | Entrega real | Status |
|---|---|---|
| MOD01 | baseline de paridade congelado antes do refactor | ✅ |
| MOD02 | decisão de portas registrada na ADR/Brain | ✅ |
| MOD03 | 2 portas em `assistant/api`, 2 impls, injeção ajustada no chamador público | ✅ |
| MOD04 | `hive-assistant-impl` removido do pom; grep limpo; única aresta nova api→api | ✅ |
| MOD05 | evidência H1–H6 e Brain | ✅ |

**Veredito completude: PASS.**

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 411 front + 526 backend · 0 falhas/erros/skips |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 133 ITs · 0 falhas |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `mvn dependency:tree` (assistant/api, publicapi/impl, history/api) | exit 0 · grafo sem ciclo |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo |
| Code review | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Gates funcionais medidos: `grep -R "assistant.impl" publicapi/impl/src/main/java` limpo; pom sem `hive-assistant-impl`; `assistant/api → ai-api, history-api, provider-api, commons-api`; `publicapi/impl → assistant-api`; `history/api` sem aresta de volta; uma `@Service` por porta sem `@Qualifier` e startup real dos ITs sem `NoUniqueBeanDefinitionException`; `TurnOutcomeReporter` com 11 parâmetros; `ChatCallerParityIT` com ordem `SystemMessage → conversação` e `120/340/460/STOP`; `OpenApiSpecIT` 9/9, `PublicApiClientAdminIT` 10/10, `PublicApiTenantQuotaIT` 4/4.

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório; `order 5` sem pulo; ADR-001..004 `merged`; `depends_on: ADR-002`; `unit: adr`.
- Marcador `<!-- scope: adr/ADR-005 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md` e `TASKS.md` presentes.
- PRD-01/ADR-01/ADR-02 PASS; único achado global é o órfão pre-existente `chat-model-override/PROPOSAL.md`, fora de escopo.
- Correção documental 9 → 11 parâmetros aplicada antes do merge e verificada.
- Rastreabilidade RF1–RF3 ↔ MOD01..MOD05 ↔ ITs ↔ `harness/public-chat-api-adr-005.json`.
- Merge provado por git: `merge-base --is-ancestor 871fef4… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. H3

N/A — sem UI nesta ADR; nenhuma mudança na UI da ADR-003.

## 5. Security Verify

Nenhuma chave, autenticação ou RBAC alterado. Exatamente uma implementação por porta, sem ambiguidade de bean. Zero diff em `Mfa*`. Nenhuma credencial nova em fixtures.

## 6. Riscos e débitos

- Startup de produção com wiring diferente do classpath dos ITs não foi testado — risco residual do review, mitigado por uma `@Service` por porta e ausência de `@Qualifier`.
- QA não releu `dependency:tree` completo (usou filtro por grupo).
- K12, crédito, SEC-02, orfão `chat-model-override` permanecem fora do PRD.

## 7. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅.** ADR-005 pronta para `merged`. Última ADR do PRD — habilita P6 (archive) após traceability ≥80%.

## 8. Resumo 10 pontos

1. Decisão: eliminar `publicapi/impl → assistant/impl` (K3).
2. Forma escolhida: portas em `assistant/api`, impls permanecem na impl.
3. Mover as classes arrastaria ~6 arquivos e criaria ciclo Maven — rejeitado.
4. Uma `@Service` por porta, sem `@Qualifier`; startup sem bean ambíguo.
5. Única aresta nova: `assistant/api → history/api`, sem ciclo.
6. `ChatTurnGateway`/trace/contrato de turno inalterados; paridade preservada.
7. 411 front + 526 backend + 133 ITs; build limpo.
8. Review APPROVED 0/0/0; correção documental 9→11 aplicada.
9. Merge PR #8 em `58c8c9d`, provado em `origin/main`.
10. Próximo: traceability ≥80% e arquivamento do PRD em `.spec/archive/20261123-prd-public-chat-api/`.
