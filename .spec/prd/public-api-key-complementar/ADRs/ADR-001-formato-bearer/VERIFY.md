# VERIFY — ADR-001: Formato Bearer `sk-` e contrato OpenAPI

| Campo | Valor |
|---|---|
| PRD | `public-api-key-complementar` (`prd_version: 2`, §16 APPROVED em 2026-11-23) |
| ADR | ADR-001 — Formato Bearer `sk-` e contrato OpenAPI da key complementar |
| Branch | `adr/public-api-key-complementar/01-formato-bearer` |
| Commit de abertura | `1840394` — `docs(api-key): abre PRD e ADR-001 do formato Bearer` |
| Commit de implementação | `fb67d71` — `feat(publicapi): aceita api key Bearer na API publica` |
| Merge | `3a891e7` — Merge pull request #9 (pais `9989e16` + `fb67d71`) |
| Data | 2026-11-23 |
| Verificador | backend-engineer (H1–H6), QA (`harness/public-api-key-complementar-adr-001.json`) |
| Evidência harness | `harness/public-api-key-complementar-adr-001.json` — `mergeVerification: MERGE_CONFIRMADO`, `adrsCommit: fb67d71`, `ancestryExit: 0` |
| Code review | APPROVED — 0 🔴 / 0 🟠 / 0 🟡 / 1 🔵 (`.spec/prd/public-api-key-complementar/ADRs/ADR-001-formato-bearer/CODE-REVIEW.md`; observação de ambiente: `.gitignore:32` ignora `signup-step-credentials.tsx`) |

## 1. Completude

- **KEY01 ✅** — baseline registrado: 411 front / 526 backend / 133 ITs; `bearer-key` publicado em `OpenApiConfig:44-50`.
- **KEY02 ✅** — porta `PublicApiKeyAuthenticator` em `publicapi/api`; filtro M2M discrimina `sk-` exato case-sensitive sem fallback; `ObjectProvider` ⇒ 401 fail-closed sem bean; aresta `security/impl → publicapi/api` explícita.
- **KEY03 ✅** — `bearer-key` mantido + `apiKeyAuth` somado, ambos HTTP bearer, só na operação de chat; token endpoint inalterado.
- **KEY04 ✅** — evidência, guardas e Brain registrados.

Nenhum ID de tarefa criado, removido ou renomeado; RF1–RF3 e NFR1–NFR5 rastreados 1:1.

## 2. Corretude

- `npm test` (suiteFull): **411/411** testes frontend, **526/526** backend — 0 falhas/erros/skips.
- `mvn verify -pl integration-test`: **139/139** ITs — 0 falhas.
- `mvn install -DskipTests`: build **0 erros**.
- ITs específicos: `PublicApiKeyAuthIT` **3/3** (200 com key e trace com clientId/tenantId/turnId; 401 para `sk-` vazio, desconhecido, `sk-eyJ...`, `SK-...`; `x-goog-api-key` ignorado); `PublicApiKeyNoBeanIT` **2/2** (sem bean e formato desconhecido); `OpenApiSpecIT` **10/10** (ambos os esquemas no group `public`, chat com os dois, token sem `apiKeyAuth`).
- Contrato OpenAI, duplo `/v1`, `role: system`, cota e token endpoint reexecutados pelos ITs existentes — verdes.

## 3. Coerência

- `ADR-INDEX.md`: 1 linha, `order 1`, `depends_on —`, `unit adr`, `dir ADRs/ADR-001-formato-bearer/`, branch e merge `3a891e7 · PR #9` — índice 1:1 com o disco, sem pulo.
- `TASKS.md` linha 1: `<!-- scope: adr/ADR-001 -->`; `## Interface congelada` preservada em uma linha.
- Gates: PRD-01 PASS (`prd_version: 2`, §16 aprovado), ADR-01 PASS (ADR/SPEC/TASKS + merge), ADR-02 PASS (`VERIFY.md` após merge).
- Merge provado por `git show -s 3a891e7` (pais `9989e16` e `fb67d71`) e `git merge-base --is-ancestor fb67d71 origin/main` (exit 0), registrados no harness.

## 4. Security Verify

- Prefixo `sk-` exato, case-sensitive, verificado primeiro; nenhum fallback para JWT (nem JWT→key). `sk-` vazio e `sk-eyJ...` ⇒ 401.
- Sem bean `PublicApiKeyAuthenticator`, qualquer `sk-` ⇒ 401 fail-closed (`ObjectProvider.getIfAvailable()`).
- Caminho de key produz o mesmo principal do JWT: `publicapi.clientId/pcode/plock/plim`, authorities (`PUBLIC_CHAT_COMPLETE`) e `TenantContext` — `@HasPermission` e `ChatTrace` preservados.
- Nenhuma credencial em log; `x-goog-api-key` não lido em nenhum caminho; nenhum `x-*` novo na spec.
- Zero diff em `Mfa*`, `workflow-state.json` (Exceto registro escopado de review), ADRs arquivadas, PRD/RESEARCH e código de emissão/cota/telemetria.

## 5. Riscos e débitos

- **Dívida pré-existente:** `.gitignore:32` (`*credentials*`) ignora `frontend/src/modules/hive/admin/credentials/` e `signup-step-credentials.tsx`. Clean checkout sem esses artefatos locais falha 2 testes de frontend; ambiente de CI/harness deve provê-los. A correção de tipos em `signup.stories.tsx` foi pré-condição de build, **fora do escopo desta ADR**.
- **Bean da porta só na ADR-002:** até lá, toda key `sk-` devolve 401 por decisão (fail-closed). Não é defeito.
- **Clean checkout:** `node_modules`/diretórios ignorados precisam ser replicados na worktree isolada para reproduzir o baseline; registrado como insumo de infra.

## 6. Veredito final

**APPROVED.** H1 ✅ · H2 ✅ · H3 N/A (sem UI nesta ADR) · H4 ✅ · H5 ✅ · H6 ✅. Code review sem 🔴 (0/0/0/1). Pronto para arquivamento do PRD.

## 7. Resumo em 10 pontos

1. PRD aprovado (`prd_version: 2`) e ADR-001 aberta em `1840394`.
2. Porta `PublicApiKeyAuthenticator` em `publicapi/api` com o mesmo principal do JWT.
3. Filtro M2M: `sk-` exato primeiro, JWT depois, sem fallback.
4. Sem bean ⇒ 401 fail-closed; ADR-002 registrará a implementação.
5. `sk-` vazio, `sk-eyJ...` e `SK-...` ⇒ 401; `x-goog-api-key` ignorado.
6. `bearer-key` inalterado + `apiKeyAuth` somado só no chat.
7. Token endpoint, paths, envelope OpenAI e cota inalterados.
8. 411 front + 526 backend + 139 ITs; build 0 erros.
9. Merge `3a891e7` (PR #9) provado por git e pelo harness.
10. Único débito: `.gitignore:32` ignora artefatos locais de credenciais; corrigir em esteira própria.
