# VERIFY — ADR-001: Contrato tipado e especificação OpenAPI da API pública

| Campo | Valor |
|---|---|
| **PRD** | `public-chat-api` (`prd_version: 2`, §16 aprovado em 2026-11-21) |
| **ADR** | `ADR-001` — `ADRs/ADR-001-contrato-openapi/` |
| **Branch** | `adr/public-chat-api/01-contrato-openapi` |
| **Commit de implementação** | `5177af7e40be0cc0e1271d3943577722ce3083fa` |
| **Merge** | `d0d38909dcc2e71926fadd29c5588e1832a44c5f` — PR #4 em `origin/main` |
| **Data verificação** | 2026-11-21 |
| **Verificador** | sdd-orquestrador + `qa-engineer` (`ses_dff9a9429ffexOyBnXOoT0feUw`) |
| **Evidência harness** | `harness/public-chat-api-adr-001.json` (`h6.status: PASS`) |
| **Code review** | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 — `.spec/prd/public-chat-api/CODE-REVIEW.md` |

---

## 1. Completude

CT01..CT05 concluídas. 37 arquivos commitados (2061 inserções / 209 remoções), sem arquivos alheios ao ciclo. Tarefas:

| Tarefa | Entrega | Status |
|---|---|---|
| CT01 | medição da hipótese 2.8.14 (refutada: 200, 26 paths, 38 schemas) + upgrade springdoc 3.2.0 | ✅ |
| CT02 | spec autenticada na chain humana; 401 anônimo e 200 com sessão | ✅ |
| CT03 | DTOs tipados substituindo `Map`, serialização congelada | ✅ |
| CT04 | duplicata removida, `GroupedOpenApi`, `@ApiResponse` 400/401 | ✅ |
| CT05 | suíte, ITs, review, Brain e evidência | ✅ |

Gaps de completude: nenhum dentro do escopo. O bugfix do `429` no token endpoint está registrado como fora da ADR (Decisão 7), com destino na ADR-004.

**Veredito completude: PASS.**

## 2. Corretude

| Verificação | Resultado |
|---|---|
| `npm test` (suiteFull do `harness.config.json`) | exit 0 · 378 front + 520 backend · 0 falhas/erros/skips |
| `mvn verify -pl integration-test` (cwd `backend/`) | exit 0 · 119 ITs · 0 falhas · `OpenApiSpecIT` 9/9 |
| `mvn install -DskipTests` | exit 0 · BUILD SUCCESS |
| `npm --prefix frontend run build` | exit 0 |
| `git diff --check` | limpo · nenhum `.flattened-pom.xml` sujo |
| Code review (duas rodadas) | APPROVED · 0 🔴 / 0 🟠 / 0 🟡 |

Gate funcional medido pelo QA: `/v3/api-docs` autenticado → 200 (27 paths, 46 schemas); group `public` = 2 paths exatos; `/v3/api-docs/public` → 200 autenticado e 401 anônimo; envelope `{message,type,param:null,code}` preservado; duplo `/v1` preservado; Decisão 7 sem handler 429 no token (400 `invalid_request`, sem `Retry-After`); Decisão 6 com 401 em `/v3/**`, swagger-resources e scalar.

**Veredito corretude: PASS.**

## 3. Coerência

- `ADR-INDEX.md` 1:1 com o diretório da ADR; marcador `<!-- scope: adr/ADR-001 -->` na linha 1 de `TASKS.md`; `ADR.md` com `depende-de` e `interface congelada`; `SPEC.md` e `TASKS.md` presentes.
- PRD-01/ADR-01/ADR-02: PASS (PRD §5 com "Como medir", §15 com `<task>`/`<context>`). `ok:false` global remanescente é órfão pre-existente `chat-model-override/PROPOSAL.md`, fora de escopo.
- Rastreabilidade: RF1–RF7 → tarefas CT01..CT05 → ITs → evidência no harness.
- Estado: `workflow-state.json.code_reviews_by_adr.ADR-001` escopado; fases e `code_review` da Fase 4 MFA intactos.
- Merge provado por git: `merge-base --is-ancestor 5177af7… origin/main` exit 0; artefatos presentes em `origin/main`.

**Veredito coerência: PASS.**

## 4. Security Verify

Sem nova superfície de autenticação: `client_credentials` e JWT `aud=hive.public` inalterados. Documentação passou a exigir sessão humana (Decisão 6); nenhuma chave ou secret novo; `PASSWORD` dos ITs é padrão pré-existente. Zero diff em `Mfa*`. Nenhuma credencial em log. Nenhuma vulnerabilidade nova introduzida.

## 5. Débitos registrados

- 429/Retry-After no token endpoint não implementado — bugfix separado, escopo da ADR-004 (Decisão 7).
- Órfão pre-existente `chat-model-override/PROPOSAL.md` sem `PRD.md`.
- Nota operacional: `mvn -pl` exige cwd `backend/`; `mvn install` regenera `.flattened-pom.xml` (restaurados).
- Crédito/budget, SEC-02 e gate ERP permanecem fora deste PRD.

## 6. Veredito final

**APPROVED — H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅.** ADR-001 pronta para `merged`. PRD permanece aberto: ADRs 002..005 pendentes.

## 7. Resumo 10 pontos

1. Decisão: contrato tipado + spec OpenAPI, springdoc 3.2.0.
2. Motivo: response era `Map`, DTO duplicado, zero anotações e springdoc fora da matriz do Boot 4.1.
3. Pré-condição: hipótese de falha do 2.8.14 medida e refutada antes do upgrade.
4. Exposição da spec decidida como autenticada na chain humana.
5. Fora de escopo: 429 do token endpoint, registrado para a ADR-004.
6. Implementação: 37 arquivos, CT01..CT05, sem arquivos alheios.
7. Testes: 898 + 119 ITs, 0 falhas; gates funcionais medidos.
8. Review: duas rodadas, 3 🟡 aplicadas, veredito 0/0/0 persistido.
9. Merge: PR #4, commit `5177af7`, merge `d0d3890` provado em `origin/main`.
10. Próximo: abrir ADR-002 (depende desta) e manter PRD aberto até ADR-005.
