# BUG — `baseUrl` de GO/Zen sem `/v1`: chat real cai em 404 e vira 422 enganoso

- **Data:** 2026-09-25
- **Severidade:** 🔴 P1 (chat com GO/Zen 100% indisponível; discovery funciona, mascarando)
- **Tipo:** backend / `provider/api` + `provider/langchain4j` + `agent/impl`
- **Origem:** encontrado no E2E com chave real (CA-GO), após o bug TLS
- **Status:** B1 aprovado (usuário "A") → B2 delegado

## Bug

Com credencial `OPENCODE_GO` válida, o agente responde:
`422 MODEL_UNAVAILABLE — "Modelo mimo-v2.5 indisponível no provedor OPENCODE_GO"`.
A mensagem é falsa: o modelo existe e a chave funciona.

## Causa raiz

`LangChain4jClientFactory.opencode(...)` monta `OpenAiChatModel.builder().baseUrl(baseUrl)`,
e o LangChain4j concatena apenas `/chat/completions` ao baseUrl (o default dele é
`https://api.openai.com/v1`, ou seja, o baseUrl deve incluir o `/v1`).

`VendorCatalog` (linhas 11-12) expõe a URL sem o segmento:

```java
public static final String OPENCODE_GO_BASE_URL  = "https://opencode.ai/zen/go";
public static final String OPENCODE_ZEN_BASE_URL = "https://opencode.ai/zen";
```

Request efetivo: `POST https://opencode.ai/zen/go/chat/completions` → **404** com
corpo HTML da landing page do opencode.ai (não JSON).

Prova (curl com a chave real do tenant `44059cfc…`):

| Path | Resultado |
|---|---|
| `/zen/go/chat/completions` | `404` + `<!DOCTYPE html>…` |
| `/zen/go/v1/chat/completions` | `200` + `{"model":"mimo-v2.5","choices":[…]}` |

O 404 é então mapeado por `ChatServiceImpl.isModelNotFound`
(`agent/impl/…/ChatServiceImpl.java:235-251`), que retorna `true` para
`HttpException` com `statusCode() == 404` → `discoveryService.evict(provider)` +
`ModelUnavailableException` 422.

**Por que o harness anterior não pegou:** `OpencodeDiscoveryServiceImpl:65` monta
o path à mão (`baseUrl + "/v1/models"`), então o discovery funcionava e mascarava
o path errado na factory. Só o chat real expõe.

## Solução proposta

1. `VendorCatalog` — incluir `/v1` nas constantes (alinhado à convenção
   LangChain4j/OpenAI e ao que o usuário digita em `Custom`):
   - `OPENCODE_GO_BASE_URL = "https://opencode.ai/zen/go/v1"`
   - `OPENCODE_ZEN_BASE_URL = "https://opencode.ai/zen/v1"`
2. `OpencodeDiscoveryServiceImpl:65` — trocar `baseUrl.trim() + "/v1/models"`
   por `baseUrl.trim() + "/models"`, para não produzir `/v1/v1/models`.
   `ProviderBaseUrlGuard.validate(baseUrl)` continua antes, sem alteração.
3. `AiProviderCatalogTest:69-70` — atualizar as asserções para as URLs com `/v1`.
4. **Degradação de diagnóstico (aprovada):** `isModelNotFound` só deve tratar
   404 como "modelo removido" quando a resposta for JSON de erro do provider.
   404 com corpo HTML (rota inexistente) NÃO pode virar "modelo indisponível" —
   deve virar `ProviderException` (500), que é o diagnóstico correto.
   - Preservar o tratamento de `ModelNotFoundException` do LangChain4j e do
     `MODEL_NOT_FOUND_PATTERN` sobre a mensagem; mudar **apenas** o caso "404 cru".

## Testes necessários

1. `AiProviderCatalogTest` — `baseUrlFor(OPENCODE_GO/ZEN)` inclui `/v1`
   (atualizar asserts existentes).
2. `OpencodeDiscoveryServiceImplTest` — o servidor de teste já expõe
   `/v1/models`; manter o teste verde **com as novas constantes** provando que
   não virou `/v1/v1/models`. Cobrir GO e ZEN.
3. Path na factory: `LangChain4jClientFactoryTest` (ou onde existir) afirmar que
   o baseUrl do `OPENCODE_GO`/`OPENCODE_ZEN` é exatamente o da constante.
4. `isModelNotFound` — unit em `ChatServiceImplTest`:
   - 404 com corpo JSON de modelo → `true` (preserva comportamento atual)
   - 404 com corpo HTML → `false` (novo: evita 422 enganoso)
   - `ModelNotFoundException` → `true`
   - 500/401/403 → `false`
5. Regressão: `OpencodeGoZenIT` (11 testes) e `KnowledgeSsrfIT` verdes.
6. H1 completo: `mvn clean install` → 0 falhas; `npm run test` + `npm run build`
   → 0 falhas (nenhum arquivo frontend deve ser tocado).
7. **Validação E2E obrigatória (CA-GO):** com a credencial já cadastrada, o
   `POST /hive/v1/chat` com o agente `mimo-v2.5` deve retornar **200** com texto
   do modelo, e a sessão deve gravar user+assistant em `chat_message`.

## Riscos

- `/v1` duplicado em discovery se a alteração 2 for esquecida — mitigado pelo
  teste 2 (o servidor de teste só responde em `/v1/models`).
- Mudança de constante afeta credencial GO/Zen já salva? **Não**: base é fixa, a
  constante é a única fonte. `Custom` não é tocado.
- `isModelNotFound` mais estrito pode reclassificar um 404 legítimo de provider
  que hoje reporta "modelo indisponível" como 500. É o comportamento correto: 404
  em rota inexistente é erro de configuração, não modelo removido. A mensagem de
  usuário do 422 permanece a mesma nos casos reais de modelo removido.
- Nenhuma mudança de schema, migration ou contrato HTTP.

## Critérios de aceite (H6)

- [ ] Constantes com `/v1`; discovery sem `/v1/v1`
- [ ] `isModelNotFound`: 404 HTML → false; 404 JSON → true
- [ ] `mvn clean install` → 0 falhas
- [ ] E2E: `POST /chat` com `OPENCODE_GO`/`mimo-v2.5` → **200** com resposta real
- [ ] `npm run test` + `npm run build` → 0 falhas, zero arquivos frontend tocados
- [ ] `fullstack-code-reviewer` com zero 🔴
- [ ] `brain_store` da lição
- [ ] Processos `:8080`/`:5173` finalizados
