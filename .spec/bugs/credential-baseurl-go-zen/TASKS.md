# BUG — Credencial GO/Zen impossível de salvar pela UI: campo `baseUrl` readonly alimenta o payload

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (feature GO/Zen 100% inutilizável pelo caminho normal — só funciona via curl)
- **Tipo:** frontend (causa) + backend (hardening de contrato)
- **Origem:** reporte do usuário · reproduzido em tela antes de qualquer mudança (R1)
- **Status:** B1 aprovado (usuário "A") → B2 delegado

## Bug

Em `/settings/providers`, clicar "Editar credencial" no **OpenCode GO** ou **OpenCode Zen**,
preencher a chave e clicar **Salvar** responde **400** e mostra toast de erro:

```
baseUrl é fixa do vendor para OPENCODE_GO; não envie baseUrl.
```

Nenhuma credencial GO/Zen pode ser salva pela interface.

## Evidência (harness, antes do fix)

- Screenshot: `.spec/bugs/credential-baseurl-go-zen/harness/repro-400-baseurl-fixa.png`
- Network: `PUT /hive/v1/provider-credentials/OPENCODE_GO` → **400**
- Request body: `{"apiKey":"oc_sk_…","baseUrl":"https://opencode.ai/zen/go/v1"}`
- Response: `application/problem+json` `ILLEGAL_ARGUMENT`
- Snapshot: toast "Erro / baseUrl é fixa do vendor para OPENCODE_GO; não envie baseUrl."
- Base de comparação: `PUT` com apenas `{"apiKey":"…"}` → **200** (é o que fiz via curl na validação anterior — erro de verificação do orchestrator: validei o endpoint por curl e a UI só "de passagem")

## Causa raiz

`frontend/src/views/admin/CredentialsView.vue` — **um único ref `baseUrl` serve dois papéis**:
é exibido como informação readonly (URL fixa do vendor) **e** é serializado no payload.

```ts
// openEdit() — linha 72: preenche com a URL fixa do vendor
baseUrl.value = statusByProvider.value.get(provider)?.baseUrl ?? VENDOR_CATALOG[provider].defaultBaseUrl ?? ''

// saveCredential() — linha 89: envia o valor, mesmo sendo readonly
if (baseUrl.value.trim()) payload.baseUrl = baseUrl.value.trim()
```

O backend está **correto**: `CredentialPolicy.validateUpsert` (linhas 29-32) rejeita
`baseUrl` para GO/ZEN, conforme SPEC ("baseUrls fixas server-side — nunca do tenant").
O defeito é inteiramente do cliente.

### Por que a suíte não pegou (160 testes verdes)

`frontend/src/views/admin/__tests__/CredentialsView.spec.ts:104`
(`lists GO/ZEN rows with read-only baseUrl, costHint and LGPD banner in dialog`) abre o
dialog de GO/Zen e afirma que a baseUrl **aparece** e é readonly — mas **nunca clica em
Salvar** nesse caminho. O único teste que assereve o payload do PUT (linha 90,
`saving with blank key keeps existing`) usa OPENAI, cujo `defaultBaseUrl` é `null`, então
`baseUrl` nunca entra no payload. **O contrato do PUT para GO/Zen nunca foi testado.**

Agravante de processo: a constante `defaultBaseUrl` foi alterada na correção do `/v1`
(mesma sessão) sem que ninguém percebesse que ela alimenta o payload.

## Solução

### Parte 1 — Frontend (corrige o bug)

`CredentialsView.saveCredential()`: **não enviar `baseUrl` para GO/ZEN**. Regra: campo
não editável não vai no payload. `OPENAI_COMPATIBLE` (custom) e `OLLAMA` continuam
enviando normalmente — para eles a baseUrl é do tenant e é significantemente usada.

Melhorplement: derivar de uma lista explícita de quem **aceita** baseUrl
(`OPENAI_COMPATIBLE | OLLAMA`) em vez de "tudo que não for GO/ZEN", para o próximo
provider não cair no mesmo buraco por omissão.

### Parte 2 — Backend (hardening de contrato)

Hoje o backend rejeita **qualquer** `baseUrl` de GO/ZEN. Mas a UI **exibe** essa URL
readonly, então um cliente que ecoe o valor exibido quebra — footgun de API.

`CredentialPolicy.validateUpsert` **e** `ValidateServiceImpl.validate` (linhas 52-56):
aceitar `baseUrl` de GO/ZEN **somente se for idêntica** a `VendorCatalog.baseUrlFor(provider)`;
se for diferente → 400 (é tentativa de override, precisa continuar bloqueada por segurança).

Justificativa: o valor é público, fixo e já exibido ao usuário; ecoar não é override.
Aceitar o eco reduz footgun sem abrir brecha — o backend continua ignorando
qualquer `baseUrl` salvo na credencial para GO/Zen (usa `VendorCatalog.baseUrlFor`).

## Testes necessários

1. **FE (o que faltou — obrigatório):** `CredentialsView.spec.ts`
   - abrir dialog de GO → preencher chave → Salvar → `upsert` chamado com `{apiKey}` e
     **sem** `baseUrl`; idem ZEN.
   - o `mockUpsert` deve resolver 200 e a UI deve fechar o dialog + toast de sucesso.
   - **Mutation check**: reintroduzir `payload.baseUrl = baseUrl.value` deve fazer o teste falhar.
2. **FE (não-regressão):** `Custom` e `Ollama` **continuam** enviando `baseUrl`.
3. **BE unit** `CredentialPolicyTest`:
   - GO/ZEN sem `baseUrl` → sem exceção
   - GO/ZEN com `baseUrl` **igual** à do vendor → sem exceção
   - GO/ZEN com `baseUrl` **diferente** → `IllegalArgumentException`
   - `OPENAI_COMPATIBLE` sem `baseUrl` → continua exigindo (400)
4. **BE IT**: `OpencodeGoZenIT` ou `CredentialIT` — `PUT /provider-credentials/OPENCODE_GO`
   com `baseUrl` igual à do vendor → **200**; com URL externa (ex. `https://evil.example.com`)
   → **400**. Este último é o teste de segurança: prova que o override continua bloqueado.
5. **E2E (H3, obrigatório):** repetir o fluxo de tela e exigir **200** no PUT, com
   screenshot antes/depois. Sem isso, o bug pode voltar.
6. **H1 completo:** `mvn clean install` → 0 falhas; `npm run test` + `npm run build` → 0 falhas.

## Riscos

- Aceitar o eco abre brecha? **Não**: o valor é derivado de `VendorCatalog` (constante
  pública), a comparação é exata, e a credencial de GO/Zen continua resolvendo a baseUrl
  pelo catálogo, ignorando o que veio no request. O IT com `evil.example.com` é a trava.
- `OLLAMA` usa `defaultBaseUrl` (`http://localhost:11434`)? Continua enviando — comportamento
  atual, não mexer.
- Mudança de contrato pode afetar outro cliente da API; o comportamento 400→200 só ocorre
  para quem envia a URL **exata** do vendor, que antes era erro garantido.

## Critérios de aceite (H6)

- [ ] FE: GO/Zen salvam sem `baseUrl` no payload
- [ ] FE: Custom/Ollama continuam enviando
- [ ] BE: eco da URL fixa aceito; URL diferente rejeitada
- [ ] IT de segurança: `evil.example.com` → 400
- [ ] E2E: salvar GO pela tela → **200** + screenshot
- [ ] `mvn clean install` + `npm run test` + `npm run build` → 0 falhas
- [ ] `fullstack-code-reviewer` → zero 🔴
- [ ] `brain_store` da lição
- [ ] Processos finalizados
