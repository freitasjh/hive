# brain_store(layer="regras", path="hive/llm/timeout-e-retry", scope="projetos")

## LLM: timeout generoso, sem retry em read timeout, e sempre aplicar maxTokens do agente

### Sintoma reportado (Hive, 2026-09-26)
```
11:11:28.719  chat inicia (glm-5.2, pedido de 1500 palavras)
11:11:58.733  HttpTimeoutException (30s) -> RetryUtils: "Remaining retries: 2 of 2"
11:12:29.258  HttpTimeoutException (30s) -> "Remaining retries: 1 of 2"
```
Latência real medida no mesmo agente: 2.3s (curto) · 9s (médio) · **>30s** (longo). ~90s pendurados
para depois dar erro.

### Causa 1 — retry em read timeout é o default errado
`langchain4j 1.19.0` — `dev.langchain4j.internal.RetryUtils.RetryPolicy.withRetry` retenta
**toda** `Exception` que não seja `NonRetriableException`:
```java
try { return action.call(); }
catch (NonRetriableException e) { throw e; }
catch (Exception e) { if (retry >= maxRetries) throw ...; log.warn("A retriable exception occurred..."); sleep(retry); }
```
`dev.langchain4j.exception.TimeoutException extends RetriableException` (não NonRetriable),
e `JdkHttpClient` converte `java.net.http.HttpTimeoutException` → `TimeoutException`.
Logo: **não existe prop no SDK para "não retentar timeout"**. O único knob real é
`maxRetries(0)` no builder, e aí some também o retry de 429/5xx.

### Causa 2 — teto de geração não aplicado
`LlmClientFactory` só recebia `temperature`; `maxTokens`/`topP` do agente nunca chegavam ao
provider. Limite configurado = **silenciosamente ignorado** → geração sem teto → estoura timeout.

### Regras
1. **Retry em read timeout de geração é o default errado.** O usuário já esperou 120s; repetir
   multiplica a espera e devolve o mesmo erro. Preferir timeout generoso + fail-fast.
2. Retry só no que é **transiente e barato de repetir**: 429, 5xx, 408. Nunca em timeout,
   `NonRetriableException` (auth/unsupported/content-filtered) nem `ModelNotFoundException`.
3. Desligar o retry do SDK (`maxRetries(0)`) e assumir o retry **seletivo** na aplicação,
   com o mesmo formato de backoff do SDK (2 retries, 500ms base, 1.5×, jitter 20%) para não
   regredir 429/5xx. Custo: ~40 linhas + um classificador de exceção.
4. **Sempre aplicar `maxTokens` do agente.** Sem teto, geração longa estoura qualquer timeout.
   `null` = default do provider. **Nunca** coercionar para 0 — Anthropic rejeita
   `max_tokens=0` e OpenAI não gera nada.
5. `maxTokens` e `topP` têm nome diferente por provider: `maxTokens` (OpenAI/OpenCode/Anthropic),
   `numPredict` (Ollama), `maxOutputTokens` (Gemini). Aplicar em **todos** — omitir em um
   deles reintroduz o bug só naquele vendor.
6. Medir antes de escolher timeout. Timeout curto demais descarta geração válida (custo pago,
   trabalho perdido) e mascarado por retry vira espera múltipla.

### Referência de campo por provider (langchain4j 1.19.0, verificado nos sources)
| provider | classe | campo de retry | setter de teto | setter topP |
|---|---|---|---|---|
| openai / openai_compatible / opencode | `OpenAiChatModel` | `maxRetries` | `maxTokens` | `topP` |
| ollama | `OllamaChatModel` | `maxRetries` | `numPredict` | `topP` |
| gemini | `GoogleAiGeminiChatModel` | **`maximumRetries`** | `maxOutputTokens` | `topP` |
| anthropic | `AnthropicChatModel` | `maxRetries` | `maxTokens` | `topP` |

Effective parameters live in `defaultRequestParameters` (`maxOutputTokens()`/`topP()` da
interface `ChatRequestParameters`) — o builder field não sobrevive no modelo. Default do
Anthropic quando `maxTokens` é null: 1024.

### Teste
- `maxRetries(0)` aplicado: ler o campo de retry no modelo construído (nome difere por provider).
- timeout não retentado: `verify(chatModel, times(1)).chat(any())` com um `TimeoutException`.
- `null` não vira 0: assert no `defaultRequestParameters.maxOutputTokens()`.

tags: [llm, timeout, retry, langchain4j, maxtokens, eng/backend]
