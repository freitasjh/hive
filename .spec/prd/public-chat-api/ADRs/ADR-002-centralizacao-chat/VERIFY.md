# VERIFY — ADR-002: Centralizar conversação, system message e telemetria

| Campo | Valor |
|---|---|
| **PRD** | `public-chat-api` (`prd_version: 2`, §16 aprovado em 2026-11-21) |
| **ADR** | `ADR-002` — `ADRs/ADR-002-centralizacao-chat/` |
| **Branch** | `adr/public-chat-api/02-centralizacao-chat` |
| **Commit de implementação** | `d39a3e9ea2ef2144ce262572505e979b92381808` |
| **Abertura da ADR** | `bd77b6e0f9f724d33b85a5e16b0ccf7f943e014d` |
| **Merge** | `c71081eafd3aa452849ae6e6511a65c187299c86` — PR #5 em `origin/main` |
| **Data verificação** | 2026-11-22 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-chat-api-adr-002.json` (`h6.status: PASS`, `mergeVerification: MERGE_CONFIRMADO`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `CODE-REVIEW.md` neste diretório |

---

## 1. Completude

CZ01..CZ05 concluídas. 9 arquivos no commit de implementação, nenhum alheio ao ciclo.

| Tarefa | Entrega real | Status |
|---|---|---|
| CZ01 | baseline existente mantido e estendido: 4 mensagens e `120/340/460/STOP` nos dois chamadores | ✅ |
| CZ02 | guarda: conversão em `history/api` sem diff; zero tipo novo; `blank` não descartado no conversor, rejeição pública preservada | ✅ |
| CZ03 | guarda de não-regressão: nenhuma montagem de mensagem fora do conversor; contrato da ADR-001 intacto | ✅ |
| CZ04 | `TurnOutcomeMapper.resolve` grava `turnId`/`clientId` em `ChatTrace` nos três ramos | ✅ |
| CZ05 | evidência H1–H6, guardas de poms/diff e Brain | ✅ |

Gaps de completude: nenhum dentro do escopo revisado pelo arquiteto.

**Veredito completude: PASS.**

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 378 front + 522 backend · 0 falhas/erros/skips |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 119 ITs · 0 falhas · `ChatCallerParityIT` 1/1 |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo · nenhum `.flattened-pom.xml` sujo |
| Code review | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Gates funcionais medidos: ordem `System/User/Ai/User` idêntica nos dois chamadores; tokens `120/340/460/STOP` em ambos; trace pública com `turnId`/`clientId` não nulos e caminho humano com `null/null`; `ChatTurnGateway`/`TurnRequest`/`TurnReply` e `history/api` sem diff; contrato da ADR-001 preservado (envelope, duplo `/v1`, `model`/`stream`/`role:system`, Decisão 7).

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório; `order 2` sem pulo após ADR-001 `merged`; `depends_on: ADR-001`; `unit: adr`.
- Marcador `<!-- scope: adr/ADR-002 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md` e `TASKS.md` presentes.
- PRD-01/ADR-01/ADR-02 PASS; único achado global é o órfão pre-existente `chat-model-override/PROPOSAL.md`, fora de escopo.
- Rastreabilidade RF1–RF4 ↔ CZ01..CZ05 ↔ ITs ↔ `harness/public-chat-api-adr-002.json`.
- Merge provado por git: `merge-base --is-ancestor d39a3e9… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. Security Verify

Nenhuma mudança de autenticação, MFA, RBAC ou chaves. A rejeição de `role: system` no request público permanece. Zero diff em `Mfa*`. Nenhuma credencial ou secret novo em código/testes.

## 5. Débitos registrados

- Órfão pre-existente `chat-model-override/PROPOSAL.md` sem `PRD.md` (fora de escopo).
- Notas operacionais: `mvn -pl` exige cwd `backend/`; `mvn install` regenera `.flattened-pom.xml` (restaurados).
- K12, SEC-02, crédito e ADR-005 permanecem como débitos/escopos futuros do PRD.

## 6. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅.** ADR-002 pronta para `merged`. PRD permanece aberto: ADRs 003..005 pendentes.

## 7. Resumo 10 pontos

1. Decisão revisada: K11 já estava fechado pela ADR-020 — nada de novo tipo ou módulo.
2. Única mudança real: `TurnOutcomeMapper.resolve` grava `turnId`/`clientId` na trace.
3. Chamador humano passa `null`/`null`; valores reais apenas no caminho público.
4. Ordem do `SystemMessage` permanece responsabilidade do gateway.
5. Guardas CZ01/CZ02/CZ03/CZ05 provam não-regressão sem refactor.
6. 900 testes e 119 ITs verdes, build limpo.
7. Code review APPROVED 0/0/0, persistido em `CODE-REVIEW.md` e no estado.
8. H6 verde com PRD-01/ADR-01/ADR-02 PASS.
9. Merge PR #5 em `c71081e`, provado em `origin/main`.
10. Próximo: ADR-003 (UI admin), depois ADR-004 (cota + bugfix 429).
