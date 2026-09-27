# Débito técnico — `unwrapData` devolve `''`, não `null`: o tipo declarado é uma mentira

**Registrado em:** 2026-09-27, na verificação V1 do batch de UX do seletor.
**Status:** 🟡 P2 (débito latente). **NÃO corrigido** — o briefing pedia reportar, não corrigir.
**Classe:** robustez de contrato cliente↔servidor.

---

## O que acontece

`TenantLlmConfigControllerV1.get()` responde `RestControllerTool.of().buildSuccessResponse(null)`
quando o tenant não tem provedor/modelo. `RestControllerTool.buildSuccessResponse(T)` é literalmente
`ResponseEntity.ok(object)` (`RestControllerTool.java:22-24`) — **não existe wrapper `{data:...}` em
nenhum ponto da resposta de sucesso**.

Medido no controller real via MockMvc (probe temporário, revertido):

```
GET /v1/tenant/llm-config  (tenant sem configuração)
  → status=200  contentType=null  contentLength=0  body=[]
```

O corpo sai **vazio**. Não é `{"data": null}`, não é `{}`.

Cadeia resultante no frontend:

| # | Onde | O que acontece |
|---|------|----------------|
| 1 | axios | `res.data === ''` — `JSON.parse('')` falha e o transformResponse devolve a string crua |
| 2 | `agent-admin.service.ts:24-28` `unwrapData` | `d = ''` é falsy → sai pelo `return data as T` → devolve `''` |
| 3 | `agent-admin.service.ts:84-85` | `body ?? null` → `''` (o `??` só pega `null`/`undefined`) → devolve `''` |
| 4 | `LlmConfigView.vue:167` | `if (config)` → `if ('')` → **falsy** → `configured = false` ✅ |

**O resultado está certo.** O que está errado é o contrato: `getLlmConfig()` declara
`Promise<TenantLlmConfig | null>` e entrega `''`. Funciona **por acidente**, porque string vazia é
falsy em JavaScript.

## Por que isso é dívida e não bug

Um payload 200 que não tem o que o cliente precisa é "ausente", e o painel de prontidão (§2.3 do
parecer de UX) trata os dois igual — mas isso é robustez de leitura, não o que o código faz hoje.
Dois riscos concretos, ambos de uma linha de mudança do outro lado:

1. **Se o wrapper um dia emitir `{}`** — por `@JsonInclude`, por um `ObjectMapper` com default
   diferente, por alguém decidir padronizar a resposta de sucesso — `unwrapData({})` devolve `{}`,
   que **é truthy**. `if (config)` passa, e `configured` vira `true` com `provider` e `model`
   `undefined`. O tenant sem configuração aparece como configurado, e o erro só aparece no clique,
   muito depois de onde a falha foi cometida. **Este é exatamente o modo de falha temido no briefing.**
2. **Se alguém escrever `config.provider`** — o que é o próximo passo natural de quem lê a assinatura
   `TenantLlmConfig | null` — estoura em `undefined`. A assinatura está asserting uma garantia que o
   servidor não dá.

## Cobertura de teste: pega o caminho errado

`frontend/src/services/__tests__/agent-admin.spec.ts:132` faz
`mockResolvedValue({ data: null })`. O servidor **nunca** produz `{"data": null}` — foi medido acima.
Ou seja:

- o caminho **real** (`''`) não tem **nenhum** teste;
- o caminho testado (`{data: null}`) não é o que acontece.

A suíte está verde num contrato que não existe e cega num que existe.

## Correção proposta (1 linha, não aplicada)

Em `agent-admin.service.ts:81-87`:

```ts
async getLlmConfig(): Promise<TenantLlmConfig | null> {
  const res = await api.get('/tenant/llm-config')
  const body = unwrapData<TenantLlmConfig | null>(res.data)
  // O servidor responde 200 com corpo VAZIO quando não há config (medido: contentLength=0).
  // Testar truthiness do objeto aqui funciona só porque '' é falsy; `{}` passaria. O que
  // interessa é se o payload tem o que o cliente precisa, não se é um objeto.
  return body?.provider && body?.model ? body : null
}
```

E o teste de serviço deve passar a mockar `''` (o que o servidor faz) **e** `{}` (o que quebraria a
versão frágil), não `{data: null}`.

**Regra geral que decorre disso:** para todo `unwrap*` do projeto, afirmar **presença do conteúdo**
(`Boolean(x?.campoQueImporta)`), nunca truthiness do objeto. Um objeto vazio é o pior dos dois
mundos: existe, e não tem nada.

## Como decidir

Precisa de decisão de produto/backend, não de refactor:

- **A)** corrigir só o cliente (a linha acima). Barato, resolve os dois riscos.
- **B)** corrigir o servidor para responder `204 No Content` ou `{"configured": false}` em vez de
  corpo vazio. Mais honesto no contrato, mas é breaking change e toca o `TenantLlmConfigIT`
  (que hoje assere `jsonPath("$.provider").doesNotExist()` — que passaria nos dois casos, e por isso
  não prova nada).
- **C)** os dois: o cliente lê por presença **e** o servidor para de devolver ambiguidade.

Enquanto isso não for decidido, o painel de prontidão (§2.3 do parecer) **não pode** testar
truthiness do objeto — a própria instrução do parecer já diz isso, e este arquivo é a razão pela qual
a instrução existe.
