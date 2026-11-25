---
prd_id: telemetria-ia-langfuse
adr_id: ADR-005
title: "Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse"
status: Proposto
data: 2026-11-25
depende-de: ADR-002
rf: "RF7, RF8"
type: adr
---

# ADR-005 — Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse

- **Data:** 2026-11-25
- **Status:** Proposto — decisão congelada; sem merge (`ADR-INDEX.md`: `todo`).
- **depende-de:** ADR-002 (exporter isolado, traces somente).
- **RFs atendidos:** RF7 (métricas em `/actuator/prometheus`, nenhuma OTLP metrics), RF8 (`sum_totalCost` derivado pelo Langfuse).

## Contexto

Spring Boot 4.1.0 + Actuator traz Micrometer com registry Prometheus e expõe `GET /actuator/prometheus` (`backend/dependencies/pom.xml:25,114`). Langfuse ingere **somente traces**: `/api/public/otel/v1/metrics` aceita a requisição e descarta os dados. Custo inferido exige observation `generation`, usage enviado **e** model definition com preço; preços default do self-host acompanham a versão instalada e só valem para generations posteriores.

## Decisão

1. **Operação fica no Micrometer/Prometheus.** As métricas congeladas abaixo são registradas no mesmo caminho instrumentado do ADR-001 (turno, callbacks do provider, redator e exporter). Exposição somente por `GET /actuator/prometheus`.
2. **Nenhum OTLP metrics ao Langfuse** (RB5). O exporter do ADR-002 envia traces e nada mais; o payload de teste não contém série de métrica.
3. **Custo é derivado no Langfuse.** O filho `generation` carrega `langfuse.observation.model.name` e `langfuse.observation.usage_details` com buckets **excludentes** `input`, `output`, `total`. O backend **nunca** envia `langfuse.observation.cost_details`/`gen_ai.usage.cost` (RF8).
4. **Model definition:** usar preços default da versão instalada quando o modelo casar; para modelos sem definição, o operador cadastra definition custom via `POST /api/public/models` (preço fornecido pelo operador, não inventado aqui). Definições novas só afetam generations posteriores.
5. **Consulta de verificação:** `GET /api/public/v2/metrics?query=<JSON>` com `view=observations`, `metrics:[{"measure":"totalCost","aggregation":"sum"}]`, dimensões por modelo e `orderBy.field=sum_totalCost`. `measure`/`aggregation` (RF8) são **campos dentro do parâmetro JSON `query`**, não query params soltos. Header v4 do ADR-002 garante disponibilidade em tempo real.
6. **`ObservationChatModelListener` não é adotado** como contrato de métrica: implementação `@Experimental` no tag 1.19.0. As métricas abaixo são registradas diretamente pela instrumentação do ADR-001, sem dependência dessa classe e sem duplicação de série semconv.

### Modelo de dados lido pelo Langfuse (contrato de consulta)

| Campo v2 | Origem / uso |
|---|---|
| `type` | `GENERATION` no filho — único tipo com usage/cost |
| `model` / `providedModelName` | `langfuse.observation.model.name` — casa com `match_pattern` da model definition |
| `usageDetails` | JSON `{"input":p,"output":c,"total":t}` — buckets excludentes |
| `costDetails` / `totalCost` | calculados pelo Langfuse; backend não envia |
| `isRootObservation`, `traceId`, `parentObservationId` | hierarquia do ADR-001; root para RF1 |
| `sum_totalCost` | campo devolvido para `measure=totalCost` + `aggregation=sum` |

## Interface congelada

### Métricas Micrometer (nominal → exposição Prometheus)

| Nome Micrometer | Exposição | Tipo | Tags | Finalidade |
|---|---|---|---|---|
| `chat.turns` | `chat_turns_total` | Counter | nenhuma | volume de turnos (RF7) |
| `chat.provider.calls` | `chat_provider_calls_total` | Counter | `provider`, `model`, `attempt` | chamadas ao provider, inclui retries |
| `chat.provider.failures` | `chat_provider_failures_total` | Counter | `provider`, `model`, `reason` | falhas com motivo classificado (sem texto) |
| `chat.provider.latency` | `chat_provider_latency_seconds` | Timer | `provider`, `model` | latência do provider |
| `chat.telemetry.export.failures` | `chat_telemetry_export_failures_total` | Counter | `sink=langfuse` | falha de export (RF9/ADR-002) |
| `chat.telemetry.redaction.failures` | `chat_telemetry_redaction_failures_total` | Counter | nenhuma | fail-closed do redator (RF4/ADR-003) |
| `chat.provider.tokens` | `chat_provider_tokens_sum` | DistributionSummary | `provider`, `model`, `token_type ∈ {input,output,total}` | cálculo independente de custo (US-02) |

> `reason` e `token_type` são conjuntos fechados; rótulos de alta cardinalidade (IDs, texto, prompt) são proibidos.
>
> **Nome canônico único (decisão architect 2026-11-25):** a série de falha de export do ADR-002/RF9 é `chat.telemetry.export.failures` (Micrometer) → `chat_telemetry_export_failures_total` (Prometheus). A grafia `chat.telemetry.export.failures.total`, antes usada no ADR-002, foi retirada. Fonte: https://prometheus.io/docs/practices/naming/ — 2026-11-25.

### Contrato de custo no Langfuse

| Nome congelado | Valor fixado |
|---|---|
| Tipo da observation | `langfuse.observation.type=generation` |
| Modelo | `langfuse.observation.model.name` ← `ChatTrace.model` efetivo |
| Usage | `langfuse.observation.usage_details` = `{"input":…,"output":…,"total":…}` (excludentes) |
| Custo enviado | nenhum campo de custo calculado pelo backend |
| Endpoint de consulta | `GET /api/public/v2/metrics`, parâmetro `query` (JSON) |
| Campo de resposta | `sum_totalCost` para `measure=totalCost`, `aggregation=sum` |
| Endpoint operacional | `GET /actuator/prometheus` |
| Sinais OTLP | traces somente; nenhuma métrica OTLP ao Langfuse |

## Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| Enviar OTLP metrics ao Langfuse | Langfuse ingere só traces; endpoint de metrics aceita e descarta — RF7 impossível. Fonte: FAQ OTEL — 2026-11-25. |
| Backend calcula e envia `cost_details` | Viola RF8 e acopla catálogo de preços ao código; ingestão tem prioridade sobre inferência. Fonte: Token & Cost Tracking — 2026-11-25. |
| `ObservationChatModelListener` como contrato de métrica | `@Experimental` no tag 1.19.0; adotá-la criaria série semconv duplicada e risco de API em upgrade. Fonte: tag 1.19.0 — 2026-11-25. |
| Só logs estruturados para operação | Não expõe série consultável por `curl`/Prometheus; RF7 exige `/actuator/prometheus`. |
| Estimar tokens a partir do texto | Langfuse retired tokenization; usage deve ser provider-reportado. Fonte: Token & Cost Tracking — 2026-11-25. |

## Consequências

**Positivas**
- Separa públicos: operação em Prometheus, análise/custo no Langfuse; nenhum backend de métricas novo.
- RF7 e RF8 são verificáveis com `curl`/MockMvc e Metrics API v2 (`sum_totalCost`).
- `chat.provider.tokens` permite cálculo independente se o catálogo do Langfuse mudar (mitigação de lock-in da US-02).

**Negativas**
- Dois destinos de observabilidade para manter; correlação entre eles é por tempo/atributos, não por ID compartilhado.
- `sum_totalCost` depende de model definition: sem match, retorna 0 mesmo com usage correto; exige cadastro pelo operador.
- Buckets `usage_details` são excludentes e `langfuse.observation.usage_details` é armazenado **sem normalização**; se o provider reportar input com cache inclusivo, o custo pode superestimar. **Decisão do operador (2026-11-25): normalizar para buckets exclusivos no backend** — `input = promptTokens - cachedTokens` quando o provider expõe cache, com `input_cached_tokens` separado; `total` permanece a soma dos buckets.
- Preços default só atualizam com upgrade da instância; definitions custom valem apenas para generations novas.

## Fontes

- Spring Boot — Actuator metrics/Micrometer — https://docs.spring.io/spring-boot/reference/actuator/metrics.html — 2026-11-25.
- Spring Boot — endpoint `/actuator/prometheus` — https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html — 2026-11-25.
- Langfuse — Token & Cost Tracking (generation, usage, model definition, buckets excludentes, preços no self-host) — https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25.
- Langfuse — Metrics API v2 (`GET /api/public/v2/metrics`, `sum_totalCost`, `isRootObservation`, atraso sem header v4) — https://langfuse.com/docs/metrics/features/metrics-api — 2026-11-25.
- Langfuse — FAQ OTEL (metrics descartadas) — https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25.
- LangChain4j — `ObservationChatModelListener` `@Experimental` no tag 1.19.0 — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25.
- Prometheus — Metric and label naming (sufixo `total` para contadores; caracteres válidos) — https://prometheus.io/docs/practices/naming/ — 2026-11-25.
- RESEARCH — eixo D5 e §7 — 2026-11-25.
