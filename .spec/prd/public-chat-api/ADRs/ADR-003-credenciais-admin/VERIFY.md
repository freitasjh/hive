# VERIFY — ADR-003: Gestão de credenciais com UI admin

| Campo | Valor |
|---|---|
| **PRD** | `public-chat-api` (`prd_version: 2`, §16 aprovado em 2026-11-21) |
| **ADR** | `ADR-003` — `ADRs/ADR-003-credenciais-admin/` |
| **Branch** | `adr/public-chat-api/03-credenciais-admin` |
| **Abertura da ADR** | `217e919a9b1925f037fc01c233b7d9cabb4f3025` |
| **Commit de implementação** | `a610266846d588ec1019a8ed768021aa23f3f0c4` |
| **Merge** | `431150ebc7619d5c567b7025ed4c5be2dc995578` — PR #6 em `origin/main` |
| **Data verificação** | 2026-11-22 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-chat-api-adr-003.json` (`h3.status: VERDE`, `h6.status: PASS`, `mergeVerification: MERGE_CONFIRMADO`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `CODE-REVIEW.md` neste diretório |

---

## 1. Completude

ADM01..ADM06 concluídas. 45 arquivos no commit de implementação (21 backend + 1 IT + 15 frontend + CODE-REVIEW + harness + estado), nenhum alheio.

| Tarefa | Entrega real | Status |
|---|---|---|
| ADM01 | `/v1/public-api/clients` com `AGENT_MANAGE`, paginação e 401/403/404 | ✅ |
| ADM02 | criar/listar/grants/pin; `client_id` gerado no servidor; sem secret em list/get | ✅ |
| ADM03 | rotação devolvendo plaintext uma vez; janela e revogação por reuso | ✅ |
| ADM04 | UI em `public-clients/`, secret one-shot isolado, estados completos, H3 verde | ✅ |
| ADM05 | `AGENT_MANAGE` verificado, zero secret/hash em log, contratos anteriores intactos | ✅ |
| ADM06 | evidência H1–H6 e Brain | ✅ |

**Veredito completude: PASS.**

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 411 front + 523 backend · 0 falhas/erros/skips |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 129 ITs · `PublicApiClientAdminIT` 10/10 |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo |
| Code review | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Contagem backend confirmada pelo QA: 522 (baseline ADR-002) + 1 teste de rotação = 523. Front 378 → 411 (+33). ITs 119 → 129 (+10).

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório; `order 3` sem pulo; ADR-001 e ADR-002 `merged`; `depends_on: ADR-001`; `unit: adr`.
- Marcador `<!-- scope: adr/ADR-003 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md` e `TASKS.md` presentes.
- PRD-01/ADR-01/ADR-02 PASS; único achado global é o órfão pre-existente `chat-model-override/PROPOSAL.md`, fora de escopo.
- Rastreabilidade RF1–RF6 ↔ ADM01..ADM06 ↔ ITs/H3 ↔ `harness/public-chat-api-adr-003.json`.
- Merge provado por git: `merge-base --is-ancestor a610266… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. H3 — browser real

Com fixture estendida (`frontend/scripts/h3-mock-api.mjs`) e sessão humana pela UI: listar, criar, rotacionar e revogar provados em browser; `client_secret` aparece só no dialog one-shot e some do DOM após dismiss; estados vazio/carregando/erro e 403 induzido verificados; 1280 e 375 sem overflow; console 0 `[ERROR]`/`[WARN]` no fluxo normal; rede sem 4xx/5xx não esperados. Screenshots: `/tmp/opencode/qa3b-clients-list-1280.png`, `qa3b-clients-final-1280.png`, `qa3b-clients-375.png`. Portas 8099/5183 encerradas por PID.

## 5. Security Verify

`AGENT_MANAGE` em todos os endpoints; path fora de `/v1/public/**` e `/manager/**`; `client_id`/`tenantId` rejeitados no payload; cross-tenant 404; secret nunca em list/get/log; zero diff em `Mfa*`. Nenhuma credencial nova em fixtures.

## 6. Débitos registrados

- `PublicApiClientAdminServiceImpl.list()` faz 1+2N queries (`findByClientRefIn` em lote é a otimização futura) — débito não bloqueante do review.
- Órfão pre-existente `chat-model-override/PROPOSAL.md` (fora de escopo).

## 7. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 ✅ · H4 ✅ · H5 ✅ · H6 ✅.** ADR-003 pronta para `merged`. PRD permanece aberto: ADRs 004..005 pendentes.

## 8. Resumo 10 pontos

1. Decisão: manter `client_credentials`, CRUD via UI admin.
2. Permissão reusada: `AGENT_MANAGE`; não criar `PUBLIC_API_MANAGE`.
3. Path `/v1/public-api/clients`, fora da chain M2M.
4. `client_id` gerado no servidor; tenant imutável.
5. `rotateSecret` devolve plaintext uma vez; janela e revogação por reuso.
6. Secret nunca em listagem/get/log; allowlist no frontend.
7. H3 real com login pela UI e CRUD em browser, 375/1280.
8. Review APPROVED 0/0/0; débito N+1 registrado.
9. Merge PR #6 em `431150e`, provado em `origin/main`.
10. Próximo: ADR-004 (cota tenant + bugfix 429), depois ADR-005.
