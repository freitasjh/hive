# BUGS — Dropdown de modelo não abre · "Testar conexão" dá timeout · master key sem diagnóstico

- **Data:** 2026-09-26
- **Severidade:** 🔴 P0 (dropdown — feature de discovery inutilizável) · 🔴 P1 (timeout) · 🟡 P2 (master key)
- **Origem:** varredura E2E de tela; **nenhum** dos 3 foi coberto por teste
- **Status:** B1 aprovado (usuário "A") → B2 delegado

> Contexto: bugs 4, 5 e 6 de uma série. Os 3 primeiros (TLS pinado, `baseUrl` sem `/v1`,
> `baseUrl` no payload) saíram de E2E. Estes saíram da varredura de tela.

---

## Bug 1 🔴 P0 — Dropdown de modelo nunca abre (todos os providers)

### Bug
Na tela "Novo Agente", o campo **Modelo** é um PrimeVue `AutoComplete` com `dropdown`. Clicar no
botão do dropdown, focar o input ou pressionar ArrowDown **nunca abre o panel**. Os 26 modelos
descobertos do OpenCode GO são **inencontráveis** por navegação.

### Evidência (Chrome DevTools, backend + frontend reais)
- `document.querySelectorAll('.p-autocomplete-overlay, .p-autocomplete-panel, [data-pc-name="overlay"]')` → **0 elementos** após clique no dropdown, após ArrowDown, e após `input.focus()`.
- Reproduzido em **OpenCode GO** (26 modelos remotos) **e** em **OpenAI** (catálogo local) → não é específico de remote.
- Zero mensagens de console (sem warning Vue, sem erro PrimeVue).
- Na tela só aparecem **3 chips**: `kimi-k3`, `kimi-k2.7-code`, `kimi-k2.6` — os 3 primeiros
  de `.slice(0, 3)`. O default `mimo-v2.5` **não** está entre eles.
- Screenshots: `.spec/bugs/model-dropdown-validate-timeout/harness/verify-autocomplete.png` e `verify-autocomplete-2.png`

### Impacto
O entregável central da feature (discovery + escolha de modelo) está inutilizável por UI. O usuário
precisa **digitar o identificador exato** para usar qualquer modelo fora dos 3 primeiros.

### Suspeita de causa (CONFIRMAR, não assumir)
`views/admin/AgentEditorView.vue`:
- linha 384: `:suggestions="remote.isRemote.value ? remote.models.value : modelSuggestions"`
- `onModelSearch` (linhas 108-112) escreve o resultado filtrado em **`modelSuggestions.value`**,
  que para providers remotos **não é lido** pelo template. Logo, para GO/Zen o filtro é código morto.
- Suspeita secundária: PrimeVue 4.5.4 com `v-model` string + `dropdown` sem `:filter` — o panel
  pode não ser criado se `filteredData` ficar vazio/inválido. **Pinar a causa real** antes de corrigir
  (ex.: ler o source de `primevue/autocomplete` em `node_modules`).

### Solução
1. Fazer o filtro chegar ao componente. Escolher **uma** fonte de verdade:
   - `:suggestions="modelSuggestions"` sempre, com `modelSuggestions` alimentado por
     `remote.models.value` para remotos; **ou**
   - manter `remote.models.value` e filtrar com `:filter`/função do PrimeVue.
   Recomendo a 1ª: uma única lista, um único filtro, sem caminho morto.
2. `:filter` explícito no AutoComplete para o fallback client-side (garante panel com resultados
   mesmo sem evento `complete`).
3. Manter `@complete` apenas para casos de catálogo grande — mas então **não** deixar código morto.
4. **Não** aumentar os 3 chips para "resolver" — a correção é o dropdown abrir e filtrar os 26.

### Testes
- `AgentEditorGoZen.spec.ts` (novo arquivo ou nova suíte) **sem mockar o composable** para o
  caminho de dados: `useRemoteModels` com `models: ['mimo-v2.5','glm-5.2','longcat-2.0']`.
- Testar o **valor de `suggestions`** que chega ao `AutoComplete` após digitar (não o DOM do panel,
  pois jsdom não renderiza overlay do PrimeVue):
  - valor vazio → todas as 26
  - digitar `g` → só as que contêm `g`
  - provider local (OpenAI) → catálogo local, filtro funciona igual
- **Mutation check obrigatório:** voltar `onModelSearch` a escrever em `modelSuggestions`
  enquanto o template lê `remote.models` deve fazer o teste falhar.
- ⚠️ O teste de DOM do panel não é confiável em jsdom; a verificação final de "abre na tela" é
  minha, com Chrome DevTools, **não** sua.

---

## Bug 2 🔴 P1 — "Testar conexão" nunca funciona com GO/Zen (timeout)

### Bug
`POST /hive/v1/agents/validate` com chave real responde
`{"ok":false,"latencyMs":5001,"reason":"timeout"}`. No browser, o request aparece como
`net::ERR_ABORTED` porque o cliente aborta em 5s.

### Evidência
- Backend (curl, sem timeout de cliente): `ok:false`, `latencyMs:5001`, `reason:"timeout"`.
- Latência real do `mimo-v2.5` medida 3x direto no upstream: **12.42s / 1.53s / 3.37s**.
- Cliente: `agent-admin.service.ts` `validate()` usa `{ timeout: 5000 }`.
- Backend: `ValidateServiceImpl` timeout de 5s.
- Chrome DevTools: `POST /hive/v1/agents/validate` → `net::ERR_ABORTED`.

### Solução
Timeout realista para latência de LLM real. Diretrizes:
- **Backend**: subir para **15s**. O validate só segura uma chamada upstream (sem transação de
  escrita) — verificar que não mantém conexão de DB ociosa além do necessário.
- **Cliente**: `{ timeout: 20000 }` (maior que o backend, para o backend responder `reason:"timeout"`
  em vez do axios abortar).
- A mensagem de `reason:"timeout"` já existe e é exibida — manter.
- **Não** aumentar acima disso: o rate limit por tenant continua valendo.
- Documentar o número e o porquê em comentário curto no código.

### Testes
- `ValidateServiceImplTest`: o timeout está parametrizado; um teste que prove que com upstream
  lento-dentro-do-limite o resultado é `ok:true` (usar delay controlado **menor** que o timeout,
  ex.: 300ms, e ajustar o timeout do teste).
- `agent-admin.spec.ts` ou equivalente: `validate()` envia `timeout: 20000`.

---

## Bug 3 🟡 P2 — Master key divergente derruba o app sem diagnóstico

### Bug
Com `PROVIDER_MASTER_KEY` diferente da que cifrou as credenciais, todo endpoint que lê credencial
retorna **500** com mensagem genérica:
`{"code":"INTERNAL_ERROR","detail":"Ocorreu um erro inesperado. Contate o suporte."}`.
A causa real (`AEADBadTagException` / `Tag mismatch`) só existe no log de servidor.

### Impacto direto
Um agente de QA reportou **3 bugs críticos inexistentes** ("master key mismatch") porque não tinha
como distinguir "chave errada no ambiente" de "aplicação quebrada". Isso consome ciclo de
diagnóstico e gera falsos positivos.

### Solução
1. **Log explícito e acionável** no ponto da falha (`AesGcmAttributeConverter`): nível ERROR,
   mensagem em pt-BR dizendo que `PROVIDER_MASTER_KEY` não corresponde às credenciais gravadas e
   que **não há recuperação automática** (a chave original é irrecuperável).
2. **Verificação no startup**: se existem credenciais e a chave não decifra a primeira, logar um
   banner FATAL/ERROR único, com a mesma orientação. Uma vez, não por request.
3. **Resposta HTTP**: manter genérica (não vazar estado da chave). Mas o `code` pode ser específico
   (`CREDENTIAL_DECRYPTION_FAILED`) **sem** revelar nada além de "falha ao decifrar" — judge se
   isso é aceitável; se houver dúvida, mantenha `INTERNAL_ERROR`.
4. Nunca logar a chave, nem parte dela, nem o ciphertext.

### Testes
- `AesGcmAttributeConverterTest`: cifrar com chave A, decifrar com chave B → `IllegalStateException`
  com mensagem que contenha a orientação (não a chave).
- Teste do startup check (se implementado) com chave divergente.
- IT: nenhum. Endpoint segue 500 com mensagem genérica.

---

## Fora de escopo (registrado)

- 🟢 `Home` hardcoded no breadcrumb (preexistente, não é GO/Zen).
- 🟢 `AgentEditorView:463` mantém `provider === 'OLLAMA' ? baseUrl : undefined` — é intencional
  (Custom não faz discovery remoto), mas merece comentário. **Não mexer** neste bugfix.

## Critérios de aceite (H6)

- [ ] Bug 1: filtro chega ao AutoComplete; sem código morto
- [ ] Bug 2: validate com chave real retorna `ok:true` (verificar com curl, 12s cabe em 15s)
- [ ] Bug 3: log acionável na falha de decifra; resposta HTTP sem vazamento
- [ ] `mvn clean install` → 0 falhas · `npm run test` + `npm run build` → 0 falhas
- [ ] Mutation check do teste do Bug 1
- [ ] `fullstack-code-reviewer` → zero 🔴
- [ ] `brain_store`
- [ ] Processos finalizados
