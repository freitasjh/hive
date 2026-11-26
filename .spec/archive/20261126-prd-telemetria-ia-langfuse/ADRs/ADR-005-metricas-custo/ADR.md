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
- **Emenda 2026-11-25 (decisão de exposição):** encerra o risco encaminhado por ADR-002/ADR.md:100; mecanismo, arquivo autorizado e impacto em MET-08 abaixo.
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
7. **Exposição do scrape é restrita à loopback (decisão B, 2026-11-25).** Hoje `SecurityConfig.java:84` dá `permitAll` a `/actuator/**` e `application.properties:27-28` expõe `prometheus` ⇒ scrape anônimo. Decisão: restringir **somente** `GET /actuator/prometheus`; manter `/actuator/health`, `/actuator/health/**` e `/actuator/info` `permitAll` (probes inalterados); **retirar `/actuator/**` da lista `permitAll`** — todo outro caminho Actuator cai em `anyRequest().authenticated()` e, fora da lista `exposure.include=health,info,prometheus`, não é publicado (404 após autenticação; 401/403 antes dela). Mecanismo: `WebExpressionAuthorizationManager` com a expressão `hasIpAddress('127.0.0.1') or hasIpAddress('::1')` no matcher de `/actuator/prometheus` da `securityFilterChain` (`@Order(2)`), antes de `anyRequest()`. Sem credencial nova, sem dependência nova, sem rota nova; fora da loopback ⇒ **403** pelo `accessDeniedHandler` existente. `application.properties` **não muda** (`health,info,prometheus`, `show-values=never`). **Arquivo autorizado por esta decisão: `backend/security/impl/src/main/java/br/com/systec/hive/security/impl/config/SecurityConfig.java` (editável).** O proxy nginx com `auth_request` do ADR-004 **não** é reutilizado: ele cobre somente UI/API do Langfuse (`proxy/langfuse-proxy.conf.template:3,37-41`), não o backend. Escopo = endpoint de scrape; nenhuma alteração em contrato de chat, credenciais ou probes de saúde.

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

### Exposição HTTP do Actuator (decisão de segurança 2026-11-25)

| Recurso | Regra congelada | Fora da regra | Arquivo |
|---|---|---|---|
| `GET /actuator/prometheus` | `permitAll` somente se `remoteAddr` ∈ {`127.0.0.1`, `::1`}, via `WebExpressionAuthorizationManager("hasIpAddress('127.0.0.1') or hasIpAddress('::1')")` | 403 com corpo existente `{"error": "Acesso negado"}` | `SecurityConfig.java` — **autorizado** |
| `GET /actuator/health`, `/actuator/health/**`, `GET /actuator/info` | `permitAll` inalterado (probes) | — | nenhum |
| `/actuator/**` (demais caminhos) | não é mais `permitAll`; cai em `anyRequest().authenticated()`; 404 pela `exposure.include` | 401/403 pela cadeia atual | nenhum |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` — sem `*`; `env`/`configprops` com `show-values=never` | 404 | `application.properties` — **sem edição** |

> Scrape local (`curl -s localhost:8080/actuator/prometheus`) continua válido. Prometheus central em outro host **não** é suportado por esta decisão: exige nova decisão (CIDR explicitado ou Basic) antes de implantar; não é "config para depois" embutida em código.

### Impacto em MET-08 (engineer ajusta SPEC/TASKS depois; ADR não os altera)

- Testes positivos de scrape permanecem: `curl -s localhost:8080/actuator/prometheus` com as 7 séries congeladas.
- Acrescentar caso de negação com `remoteAddr` externo (ex.: `203.0.113.10`) ⇒ 403; provar que `/actuator/health` e `/actuator/info` seguem 200 sem credencial e que `/actuator/env` responde 404.
- `RES/application.properties` não muda. Único arquivo de código autorizado: `SecurityConfig.java`.

## Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| Enviar OTLP metrics ao Langfuse | Langfuse ingere só traces; endpoint de metrics aceita e descarta — RF7 impossível. Fonte: FAQ OTEL — 2026-11-25. |
| Backend calcula e envia `cost_details` | Viola RF8 e acopla catálogo de preços ao código; ingestão tem prioridade sobre inferência. Fonte: Token & Cost Tracking — 2026-11-25. |
| `ObservationChatModelListener` como contrato de métrica | `@Experimental` no tag 1.19.0; adotá-la criaria série semconv duplicada e risco de API em upgrade. Fonte: tag 1.19.0 — 2026-11-25. |
| Só logs estruturados para operação | Não expõe série consultável por `curl`/Prometheus; RF7 exige `/actuator/prometheus`. |
| Estimar tokens a partir do texto | Langfuse retired tokenization; usage deve ser provider-reportado. Fonte: Token & Cost Tracking — 2026-11-25. |
| Manter `/actuator/**` `permitAll` (scrape público) | ADR-002/ADR.md:100 encaminhou o risco sem decisão. Com `SecurityFilterChain` customizada o auto-config do Boot recua; endpoints expostos devem estar atrás de firewall ou de regra de segurança. Deixar público expõe padrão de volume, latência e provider a qualquer chamador sem necessidade. Fonte: Spring Boot Endpoints §Security — 2026-11-25. |
| Reutilizar `auth_request` do ADR-004 | Proxy cobre só a instância Langfuse (`proxy/langfuse-proxy.conf.template:3,37-41`); o backend não passa por ele. Estender o proxy ao backend é outro contrato, fora deste ADR. Fonte: arquivo local — 2026-11-25. |
| Basic auth com usuário actuator | Criaria credencial sem consumidor (nenhum coletor Prometheus configurado no repo) e novo `UserDetailsService` poderia alterar a cadeia de autenticação do chat. Regra de rede resolve com menos código e sem secret novo. Fonte: Spring Boot Endpoints §Security — 2026-11-25. |
| Porta de management separada (`management.server.port`) | Move também `/actuator/health` para a outra porta, exigindo realocação de probes — risco de quebrar a saúde que esta decisão deve preservar. Fonte: Spring Boot Monitoring over HTTP — 2026-11-25. |

## Consequências

**Positivas**
- Separa públicos: operação em Prometheus, análise/custo no Langfuse; nenhum backend de métricas novo.
- RF7 e RF8 são verificáveis com `curl`/MockMvc e Metrics API v2 (`sum_totalCost`).
- `chat.provider.tokens` permite cálculo independente se o catálogo do Langfuse mudar (mitigação de lock-in da US-02).
- Superfície pública do Actuator cai para `health`/`info`; scrape deixa de ser anônimo fora da loopback; nenhuma credencial nova entra no repo.

**Negativas**
- Dois destinos de observabilidade para manter; correlação entre eles é por tempo/atributos, não por ID compartilhado.
- `sum_totalCost` depende de model definition: sem match, retorna 0 mesmo com usage correto; exige cadastro pelo operador.
- Buckets `usage_details` são excludentes e `langfuse.observation.usage_details` é armazenado **sem normalização**; se o provider reportar input com cache inclusivo, o custo pode superestimar. **Decisão do operador (2026-11-25): normalizar para buckets exclusivos no backend** — `input = promptTokens - cachedTokens` quando o provider expõe cache, com `input_cached_tokens` separado; `total` permanece a soma dos buckets.
- Preços default só atualizam com upgrade da instância; definitions custom valem apenas para generations novas.
- Scrape restrito à loopback: coletor Prometheus em outro host não alcança o endpoint sem nova decisão (CIDR explícito ou Basic). `curl localhost` e MockMvc continuam servindo RF7/RF8.

## Fontes

- Spring Boot — Actuator metrics/Micrometer — https://docs.spring.io/spring-boot/reference/actuator/metrics.html — 2026-11-25.
- Spring Boot — endpoint `/actuator/prometheus` — https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html — 2026-11-25.
- Spring Boot — Endpoints §Security (exposição só `health` por default; `SecurityFilterChain` custom desliga o auto-config; endpoints expostos devem estar behind firewall ou Spring Security) — https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html — 2026-11-25.
- Spring Boot — Monitoring and Management over HTTP (`management.server.port` move também o Actuator, inclusive health) — https://docs.spring.io/spring-boot/4.1/reference/actuator/monitoring.html — 2026-11-25.
- Spring Security 7.0 — `WebSecurityExpressionRoot.hasIpAddress(String)` (IPv4/IPv6, IP/Netmask) — https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/web/access/expression/WebSecurityExpressionRoot.html — 2026-11-25.
- Spring Security — `WebExpressionAuthorizationManager` (avaliador de expressão para `requestMatchers(...).access(...)`) — https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/access/expression/WebExpressionAuthorizationManager.html — 2026-11-25.
- Langfuse — Token & Cost Tracking (generation, usage, model definition, buckets excludentes, preços no self-host) — https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25.
- Langfuse — Metrics API v2 (`GET /api/public/v2/metrics`, `sum_totalCost`, `isRootObservation`, atraso sem header v4) — https://langfuse.com/docs/metrics/features/metrics-api — 2026-11-25.
- Langfuse — FAQ OTEL (metrics descartadas) — https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25.
- LangChain4j — `ObservationChatModelListener` `@Experimental` no tag 1.19.0 — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25.
- Prometheus — Metric and label naming (sufixo `total` para contadores; caracteres válidos) — https://prometheus.io/docs/practices/naming/ — 2026-11-25.
- RESEARCH — eixo D5 e §7 — 2026-11-25.
