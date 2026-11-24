# VERIFY — ADR-002: Emissão, hash, rotação e revogação da API key

| Campo | Valor |
|---|---|
| PRD | `public-api-key-complementar` (`prd_version: 2`, §16 APPROVED em 2026-11-23) |
| ADR | ADR-002 — Emissão, hash, rotação e revogação da API key |
| Branch | `adr/public-api-key-complementar/02-emissao-rotacao` |
| Commit de abertura | `ec14814` — `docs(api-key): abre ADR-002 de emissao e rotacao` |
| Sync de infra | `3d29f44` — `chore: sincroniza ADR-002 com saneamento de ITs` (merge PR #11) |
| Commit de implementação | `749a131` — `feat(publicapi): emite e rotaciona api key com one-shot` |
| Merge | `aae4379` — Merge pull request #12 (pais `3667f4d` + `749a131`) |
| Data | 2026-11-24 |
| Verificador | backend-engineer (H1–H6), QA (`harness/public-api-key-complementar-adr-002.json`) |
| Evidência harness | `harness/public-api-key-complementar-adr-002.json` — `mergeVerification: MERGE_CONFIRMADO`, `adrsCommit: 749a131`, `ancestryExit: 0` |
| Code review | APPROVED — 0 🔴 / 0 🟠 / 0 🟡 / 0 🔵, após correção de 1 🟠 (fallback O(N) no autenticador) |

## 1. Completude

- **KEY2-01 ✅** — baseline registrado: 411 front / 526 backend / 144 ITs, `sk-` sem bean devolve 401, claims do emissor preservados.
- **KEY2-02 ✅** — migration V3 em `publicapi_db`, entidade/repositório, bean único de `PublicApiKeyAuthenticator`, BCrypt custo 10, janela fixa de 10 min, `policyFor(client)` compartilhado.
- **KEY2-03 ✅** — `PublicApiKeyControllerV1` em `/v1/public-api/clients`, `AGENT_MANAGE`, emissão one-shot; list/get sem plaintext nem hash.
- **KEY2-04 ✅** — rotação com janela e revogação `POST .../revoke` → `revoked_at` + `hive:public:revoked-keys`.
- **KEY2-05 ✅** — evidência, guardas e Brain.

Nenhum ID de tarefa criado/removido/renomeado; RF1–RF4 e NFR1–NFR5 rastreados 1:1.

## 2. Corretude

- `npm test` (suiteFull): **411/411** frontend, **530/530** backend — 0 falhas/erros/skips.
- `mvn verify -pl integration-test`: **150/150** ITs — 0 falhas.
- `mvn install -DskipTests`: build **0 erros**.
- ITs específicos: `PublicApiKeyLifecycleIT` **6/6** (V3 em Postgres real, one-shot, autenticação, janela dentro/fora, revoke Redis+DB, cross-tenant 404/403, claims idênticos); `PublicApiKeyAuthIT` **3/3**; `PublicApiKeyAuthenticatorImplTest` **4/4** (key inventada → 401 com 1 query indexada e `never().findAll()`, key atual, anterior dentro/fora da janela).
- Contrato HTTP, envelope OpenAI e duplo `/v1` reexecutados — verdes.

## 3. Coerência

- `ADR-INDEX.md`: linha 1 ADR-001 `merged`/`3a891e7 · PR #9` intacta; linha 2 `order 2`, `depends_on ADR-001`, `unit adr`, `dir ADRs/ADR-002-emissao-rotacao/`, branch e merge `aae4379 · PR #12` — índice 1:1 com o disco, sem pulo.
- `TASKS.md` linha 1: `<!-- scope: adr/ADR-002 -->`; `## Interface congelada` preservada em uma linha.
- Gates: PRD-01 PASS, ADR-01 PASS, ADR-02 PASS (`VERIFY.md` após merge).
- Merge provado por `git show -s aae4379` (pais `3667f4d` e `749a131`) e `git merge-base --is-ancestor 749a131 origin/main` (exit 0), registrados no harness.

## 4. H3

**N/A** — nenhuma UI nesta ADR (UI admin de keys fica fora do PRD).

## 5. Security Verify

- Plaintext **somente** em `POST /{clientId}/keys` e `POST /{clientId}/keys/{keyId}/rotate`; list/get e logs sem plaintext ou hash.
- BCrypt custo 10 do valor completo `sk-...`; verificação no hash da key ou, dentro da janela de 10 min, no `previous_key_hash`/`previous_key_id`.
- Sem `findAll()` no autenticador: 1 query indexada (`key_id`/`previous_key_id`) e no máximo 1 comparação BCrypt — inclusive para key inventada, antes do rate limit.
- Revogação imediata: `revoked_at` no DB + set Redis `hive:public:revoked-keys`, distinto de `revoked-clients`.
- Cross-tenant → 404; sem `AGENT_MANAGE` → 403; nenhuma credencial em log.

## 6. Riscos e débitos

- **EXPLAIN do OR** (review): `findByKeyIdOrPreviousKeyId` gera OR entre colunas indexadas; ambos os índices existem, mas o plano deve ser conferido com `EXPLAIN` em volume maior. Sem impacto funcional medido.
- **Relógio da janela**: expiração de 10 min depende do timestamp do DB; divergência de relógio pode encurtar/alongar a janela. Fora desta ADR.
- **`.gitignore:32`**: ignora `signup-step-credentials.tsx` e diretórios `credentials`; clean checkout de frontend exige replicação local — dívida pré-existente registrada.
- **Bean único**: apenas uma implementação `@Service` da porta; um segundo bean faria `getIfAvailable()` devolver 401 fail-closed. Teste de contexto cobre.

## 7. Veredito final

**APPROVED.** H1 ✅ · H2 ✅ · H3 N/A · H4 ✅ · H5 ✅ · H6 ✅. Code review sem 🔴/🟠/🟡/🔵. Pronto para arquivamento do PRD.

## 8. Resumo em 10 pontos

1. PRD aprovado e ADR-001 merged (`3a891e7`) entregou o formato `sk-` e a porta.
2. ADR-002 aberta em `ec14814`, sincronizada com a infra de IT em `3d29f44`.
3. Migration V3 cria `public_api_client_key` com nomes `key_*` e CHECK do par previous.
4. Bean único autentica `sk-` com BCrypt 10 e devolve `null` na falha.
5. `policyFor(client)` compartilha permissions/pcode/plock/plim; `plim` m/c/t/r inalterado.
6. Emissão e rotação one-shot; janela fixa de 10 min com `previous_key_id`/hash.
7. Revogação via `POST` grava DB e `hive:public:revoked-keys` — efeito no próximo request.
8. Review 🟠 corrigido: sem varredura O(N); 1 query indexada e no máximo 1 BCrypt.
9. 411 front + 530 backend + 150 ITs; build 0 erros; V3 aplicada em Postgres real.
10. Merge `aae4379` (PR #12) provado por git e harness; riscos residuais registrados.
