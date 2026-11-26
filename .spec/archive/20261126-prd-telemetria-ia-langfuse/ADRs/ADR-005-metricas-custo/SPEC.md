<!-- scope: adr/ADR-005 -->
# SPEC — ADR-005: Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse

> **Escopo:** somente a decisão congelada no `ADR.md` deste diretório. Cada `RF`/`NFR` abaixo aponta para a cláusula do ADR que o origina. Não redefine o modelo de spans/correlação (ADR-001), o transporte OTLP/header v4/exporter (ADR-002), a redação de texto (ADR-003) nem proxy/RBAC/retention (ADR-004).
>
> **Rastreabilidade:** `ADR.md` (decisão + interface congelada + decisão do operador de 2026-11-25 + emenda de exposição de 2026-11-25) → esta SPEC → `TASKS.md` → evidência no `VERIFY.md` pós-merge. `depende-de: ADR-002`. Status do ADR: Proposto (sem merge).
>
> **Deepsearch (2026-11-25):** assinatura da Metrics API v2, contrato de `usage_details`, prioridade de custo ingerido e vigência de model definitions foram verificados na doc oficial do Langfuse (§12); a assinatura de `WebExpressionAuthorizationManager(String)` e de `WebSecurityExpressionRoot.hasIpAddress(String)` da emenda de exposição foi verificada na doc oficial do Spring Security (§12). Nenhuma assinatura de API foi aceita de memória; onde o ADR não fixa valor (preço do modelo), a SPEC não inventa — o preço vem do operador.

## 1. ADRs

### ADR-005 (este ciclo)

- **Decision:** métricas operacionais registradas em Micrometer e expostas **somente** por `GET /actuator/prometheus`, nas sete séries congeladas (nominal → exposição, tipo e tags na "Interface congelada" do ADR); **nenhum OTLP metrics ao Langfuse** — o endpoint `/api/public/otel/v1/metrics` aceita e descarta, e o exporter do ADR-002 envia traces e nada mais (RB5). Custo **derivado no Langfuse**: filho `generation` com `langfuse.observation.model.name` e `langfuse.observation.usage_details` de buckets **excludentes** `input`/`output`/`total`, sem nenhum campo de custo calculado pelo backend. Consulta de verificação via `GET /api/public/v2/metrics?query=<JSON>` com `view=observations`, `metrics:[{measure:"totalCost",aggregation:"sum"}]`, dimensão por modelo e `orderBy.field=sum_totalCost`; `measure`/`aggregation` vivem **dentro do JSON do parâmetro `query`**, não como query params soltos. Buckets são normalizados no backend conforme decisão do operador (2026-11-25). `ObservationChatModelListener` não é adotado como contrato de métrica (`@Experimental` no tag 1.19.0). (ADR Decisão 1–6, Interface congelada, Consequências, Nota de nome canônico) **Decisão 7 (emenda de exposição, 2026-11-25):** `GET /actuator/prometheus` exige `remoteAddr` ∈ {`127.0.0.1`, `::1`} via `WebExpressionAuthorizationManager("hasIpAddress('127.0.0.1') or hasIpAddress('::1')")` na `securityFilterChain` `@Order(2)`; fora da loopback ⇒ 403. `/actuator/health`, `/actuator/health/**` e `/actuator/info` seguem `permitAll`; `/actuator/**` sai do `permitAll` e os demais caminhos caem em `anyRequest().authenticated()` com 404 pela `exposure.include`. `application.properties` inalterado; único arquivo de código autorizado: `backend/security/impl/src/main/java/br/com/systec/hive/security/impl/config/SecurityConfig.java`. (ADR Decisão 7 + "Exposição HTTP do Actuator")
- **Context:** Spring Boot 4.1.0 + Actuator já traz Micrometer com registry Prometheus e o endpoint `/actuator/prometheus`. O Langfuse ingere somente traces; custo inferido exige `generation` + usage + model definition com preço; preços default do self-host são os da versão instalada e só valem para generations posteriores. `langfuse.observation.usage_details` é armazenado **sem normalização** — se o provider reportar input com cache inclusivo, o custo superestima. (ADR Contexto e Consequências negativas; §12)
- **Trade-offs:** separa público de operação (Prometheus) de análise/custo (Langfuse), sem backend de métricas novo e sem catálogo de preços no código; em troca há dois destinos de observabilidade correlacionados por tempo/atributos, não por ID, e `sum_totalCost` é 0 sem model definition. `chat.provider.tokens` mitigação lock-in da US-02 permitindo cálculo independente. Alternativas descartadas no ADR (OTLP metrics ao Langfuse, `cost_details` pelo backend, `ObservationChatModelListener` como contrato, só logs, estimar tokens do texto) não são reabertas aqui. (ADR Alternativas e Consequências)

ADRs afetados: **ADR-002** (`depende-de` — exporter traces-only, header v4, flag `HIVE_TELEMETRY_ENABLED`, incremento de `chat.telemetry.export.failures`); **ADR-001** (pontos de instrumentação: turno, callbacks do provider, `attempt`, `model`, usage); **ADR-003** (fail-closed do redator incrementa `chat.telemetry.redaction.failures`); **ADR-004** (consome traces; nenhuma alteração de contrato aqui; o proxy `auth_request` cobre só Langfuse e não é reutilizado para o scrape — ADR Decisão 7). Nenhum arquivo desses ADRs é alterado neste ciclo.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `assistant` — observabilidade operacional e financeira do turno de chat. Métricas são saída de infraestrutura instrumentada; custo é derivado fora do backend, no Langfuse.
- **System Type:** backend Spring Boot 4.1.0 + Actuator/Micrometer; instrumentação em `assistant/impl` e no exporter de `bootstrap`; configuração de exposição em `backend/bootstrap/src/main/resources/` e regra de acesso ao scrape em `backend/security/impl/src/main/java/br/com/systec/hive/security/impl/config/SecurityConfig.java` (ADR Decisão 7); consulta de custo é operação externa (`curl`/IT) contra o self-host v4. Verificação local: `caminho:linha` em §12.
- **Tenant Provisioning:** N/A — nenhum tenant, projeto ou credencial é criado. `tenantId` é lido do dado do ADR-001 e **nunca** vira rótulo de série (NFR-02).
- **Tenant Lifecycle:** N/A — inalterado; nenhuma regra de tenant, RBAC da aplicação ou provisionamento (PRD RB2/RB7).

## 3. Identity & Access Management

- **IdP / Protocol:** inalterados. A consulta à Metrics API v2 usa a mesma autenticação Basic do self-host definida no ADR-002; credencial de leitura é distinta da de ingestão (PRD RF5/RB3). Nenhum valor de `pk`/`sk` entra nesta SPEC, em log ou em comando de teste (comandos usam variáveis de ambiente).
- **RBAC:** nenhuma permissão nova nem `@HasPermission` novo nesta fatia — a restrição do scrape é regra de origem IP na `securityFilterChain` `@Order(2)` de `SecurityConfig.java`, não authority de aplicação (ADR Decisão 7). Administração de model definition (`POST /api/public/models`) é ato do operador/time de IA, não do backend (ADR Decisão 4).
- **MFA:** N/A — proibido tocar o ciclo de MFA (PRD §2, fora de escopo).
- **Sessão/cliente:** `sessionId`, `tenantId`, `turnId`, `clientId`, `promptTemplateId` permanecem no contrato do ADR-001 e são explicitamente proibidos como rótulo de métrica (NFR-02).

## 4. Security, Key Management & Disaster Recovery

- **Chaves:** nenhuma chave nova. `LANGFUSE_INGESTION_PK`/`SK` continuam referências a secret (ADR-002); a chave de consulta da Metrics API v2 é de leitura e vem de secret, nunca de literal de repo, log, payload ou resposta. Actuator `env`/`configprops` mantêm `show-values=never`. A restrição do scrape é por IP: nenhum usuário/credencial actuator é criado (ADR Decisão 7).
- **Texto/PII:** rótulos de falha reusam o classificador fechado do ADR-001 (`FailureClassifier.causeOf`); nenhum prompt, resposta, ID ou stack trace vira rótulo, nome de série ou valor de `usage_details`. Texto de prompt/resposta pertence ao ADR-003 e não é introduzido aqui.
- **Custo:** backend envia **zero** custo: proibidos `langfuse.observation.cost_details`, `gen_ai.usage.cost`, `costDetails` e qualquer campo calculado de preço no payload OTLP (RF-09/NFR-03). Preço nunca é inventado pelo código — quando o modelo não casa com definition default, o operador cadastra via `POST /api/public/models` (ADR Decisão 4).
- **Transporte:** `/actuator/prometheus` é endpoint de scrape **restrito à loopback**: `remoteAddr` ∈ {`127.0.0.1`, `::1`} passa; qualquer outro endereço recebe 403 com o corpo existente `{"error": "Acesso negado"}` (decisão B — ADR Decisão 7). `/actuator/health`, `/actuator/health/**` e `/actuator/info` permanecem `permitAll` (probes intactos); `/actuator/**` sai da lista `permitAll` e os demais caminhos caem em `anyRequest().authenticated()` (404 pela `exposure.include` após autenticação; 401/403 antes dela). `application.properties` inalterado (`health,info,prometheus`, `show-values=never`). Consulta ao Langfuse usa TLS como em ADR-002. Nenhuma métrica é exportada para fora do backend via OTLP (RF-08/NFR-04). Coletor em outro host exige nova decisão (CIDR explícito ou Basic) — nada de "config para depois" embutido em código.
- **RTO/RPO:** N/A — nenhum estado novo persistido pela aplicação; custo e traces vivem no Langfuse, métricas ficam no registry em memória do Micrometer (PRD §11).

## 5. Data Model & Concurrency

- Nenhuma entidade, tabela, migration Flyway/Liquibase ou chave Redis é criada. Artefatos desta SPEC são séries Micrometer, atributos OTLP na span `generation` e a resposta JSON da Metrics API v2.
- **Contrato de `usage_details` (JSON enviado no atributo `langfuse.observation.usage_details`):**

| Chave | Presença | Regra (decisão do operador 2026-11-25) |
|---|---|---|
| `input` | sempre que houver usage de prompt | `promptTokens - cachedTokens` quando o provider expõe cache; senão `promptTokens`. Nunca inclui `input_cached_tokens` |
| `output` | sempre que houver usage de completion | `completionTokens` (excludente de `output_*`) |
| `input_cached_tokens` | somente quando o provider expõe cache | `cachedTokens`, bucket separado — nunca somado dentro de `input` |
| `total` | sempre | soma dos buckets: `input + output [+ input_cached_tokens]`; igual a `total_tokens` do provider quando reportado |

> **Exemplo executável (doc oficial, §12):** provider `prompt_tokens=17903`, `cached_tokens=17817`, `completion_tokens=188`, `total_tokens=18091` ⇒ `{"input":86,"output":188,"input_cached_tokens":17817,"total":18091}`. Sem cache: `{"input":p,"output":c,"total":p+c}`, sem a chave `input_cached_tokens`. "Total" **não** é bucket — é a soma; nenhum token é contado em dois buckets.
>
> `chat.provider.tokens` mantém o conjunto fechado de rótulos `token_type ∈ {input,output,total}` do ADR: `input_cached_tokens` **não** vira quarto rótulo Prometheus. A separação de cache vale somente para `usage_details` no Langfuse (RF-07 × RF-10).

- **ERD:** N/A — sem entidade nova neste ciclo (artefatos são série, atributo OTLP e JSON de consulta; traces/custo vivem no Langfuse — PRD §11).
- **Migrations:** N/A — sem migration Java neste ciclo.
- **Concorrência:** registry Micrometer é thread-safe por construção; contadores/timers são incrementados no mesmo ponto instrumentado do turno (ADR-001/002/003), sem lock, transação ou fila nova. Nenhum valor de um turno entra na série de outro — validado por IT com dois turnos (RF-01/RF-07).

## 6. Event Model, Integrations & Messaging Isolation

- **Event Registry:** N/A — nenhum evento de domínio novo; séries Micrometer e atributos OTLP não são eventos de domínio.
- **Integração de saída:** `GET /actuator/prometheus` (scrape local) e, somente para verificação/custo, `GET /api/public/v2/metrics` (leitura) e `POST /api/public/models` (cadastro pelo operador). Nenhuma integração de escrita de métrica é adicionada.
- **Isolamento:** exporter OTLP continua traces-only (ADR-002); nenhum registro global de auto-instrumentação, nenhum OTLP metrics/logs. Métricas não carregam ID de tenant/turno/sessão (NFR-02).
- **Hierarquia lida pelo Langfuse:** `type=GENERATION` no filho; `isRootObservation`, `traceId`, `parentObservationId` do ADR-001; custo e `costDetails` calculados pelo Langfuse, não pelo backend.

## 7. Invariants & Property-Based Testing

### 7.1 Invariantes das séries e da observação

| ID | Propriedade | Verificação |
|---|---|---|
| INV-1 | Catálogo é exatamente 7 séries, com os pares nominal→Prometheus congelados no ADR; 0 série extra e 0 rename | teste de caracterização de strings + `curl /actuator/prometheus` |
| INV-2 | Contadores usam sufixo `_total` na exposição (`chat_turns_total`, `chat_provider_calls_total`, `chat_provider_failures_total`, `chat_telemetry_export_failures_total`, `chat_telemetry_redaction_failures_total`); timer expõe família `chat_provider_latency_seconds`; summary expõe `chat_provider_tokens_sum` | scrape + caracterização |
| INV-3 | Conjunto de tags de cada série é exatamente o da tabela do ADR; `reason` e `token_type` são conjuntos fechados; `sink=langfuse` é o único valor de `sink` | asserção por série no teste unitário + scrape |
| INV-4 | Nenhum rótulo contém ID, texto, prompt, resposta, stack trace ou `sk-`; `tenantId`/`turnId`/`sessionId`/`clientId` ausentes de toda série | varredura de nomes/valores de tag no scrape e no teste |
| INV-5 | `usage_details` tem buckets mutuamente excludentes: `input = promptTokens - cachedTokens`, `input_cached_tokens = cachedTokens`, `output = completionTokens`, `total = soma dos buckets`; `total` nunca é bucket duplicado | IT com fixture OpenAI-style 17903/17817/188/18091 e com caso sem cache |
| INV-6 | Payload OTLP não contém nenhum campo de custo calculado pelo backend: 0 `langfuse.observation.cost_details`, 0 `gen_ai.usage.cost`, 0 `costDetails` | decode do payload no IT + `grep` em `src/main/` |
| INV-7 | Consulta de custo usa `query` como único parâmetro JSON; `measure=totalCost` e `aggregation=sum` dentro de `metrics[]`; campo devolvido é `sum_totalCost`; 0 `?measure=`/`?aggregation=` soltos | `curl -G --data-urlencode` no IT + asserção da resposta |
| INV-8 | `HIVE_TELEMETRY_ENABLED` permanece como em ADR-002 (default `false`; `true`=100%); este ADR não cria segundo botão, taxa intermediária nem novo env de métrica | caracterização herdada + auditoria de config |
| INV-9 | Nenhuma requisição a `/api/public/otel/v1/metrics` ou `/v1/logs`; 0 exporter de metrics registrado; 0 uso de `ObservationChatModelListener` | varredura do IT + `grep` em `src/main/` |
| INV-10 | Nome canônico da falha de export é par único: Micrometer `chat.telemetry.export.failures` → Prometheus `chat_telemetry_export_failures_total`; grafia antiga `chat.telemetry.export.failures.total` retirada | caracterização + `grep` por nome antigo (0 ocorrência ativa) |
| INV-11 | Acesso a `GET /actuator/prometheus` só com `remoteAddr` ∈ {`127.0.0.1`, `::1`}; fora ⇒ 403 com `{"error": "Acesso negado"}`. `/actuator/health`, `/actuator/health/**` e `/actuator/info` seguem 200 sem credencial; `/actuator/env` ⇒ 404 com usuário autenticado (401/403 sem credencial) | IT/MockMvc com `remoteAddr` `127.0.0.1`, `::1` e `203.0.113.10` + asserção de status/corpo |

### 7.2 Invariantes de workflow

- **Turno:** abertura/emissão do turno e das tentativas pertence ao ADR-001; este ADR apenas incrementa as séries nos mesmos pontos, sem abrir span novo e sem alterar `ChatTrace`.
- **Falha de export:** ADR-002 incrementa `chat.telemetry.export.failures`; ADR-005 fixa nome/tipo/tag/exposição da série. Falha nunca altera resposta do chat (RF9) — a série é observação, não retry.
- **Fail-closed do redator:** ADR-003 omite `langfuse.observation.input/output` e incrementa `chat.telemetry.redaction.failures`; metadados (`provider`/`model`/usage) permanecem e seguem alimentando custo.
- **Custo:** sem usage ou sem model definition, `sum_totalCost` é 0 — comportamento esperado, não defeito de instrumentação. Definition nova vale apenas para generations posteriores; operador cadastra, backend nunca inventa preço.

### 7.3 Restrições de geração de dados de teste

| Campo | Tipo | Restrição | Objetivo |
|---|---|---|---|
| `reason` | String | conjunto fechado do classificador de falha; sem `at br.com`, sem texto de prompt | INV-3/INV-4 |
| `token_type` | enum | exatamente `input` \| `output` \| `total` (3 valores) | INV-3, RF-07 |
| `sink` | enum | exatamente `langfuse` | INV-3 |
| `attempt` | int | ≥ 1 | RF-02, herda ADR-001 |
| `promptTokens`/`cachedTokens`/`completionTokens` | int | `0 ≤ cachedTokens ≤ promptTokens`; `total = promptTokens + completionTokens` | INV-5 |
| `usage_details` | JSON | chaves permitidas `input`, `output`, `input_cached_tokens` (condicional), `total`; 0 chave de custo | INV-5/INV-6 |
| `query` (Metrics API v2) | JSON string | `view=observations`; `metrics[0].measure=totalCost`; `metrics[0].aggregation=sum`; `orderBy[0].field=sum_totalCost`; dimensão de modelo | INV-7 |
| preço da definition custom | decimal | fornecido pelo operador via variável de teste; 0 literal inventado no código | ADR Decisão 4 |
| `remoteAddr` (scrape) | String | `127.0.0.1` e `::1` aceitos; `203.0.113.10` (TEST-NET-3) negado — nenhum CIDR novo inventado | INV-11, RF-08 |

### 7.4 Estratégia de testes

- **Unitários:** caracterização das 7 séries (nomes, tipos, tags), do catálogo de rótulos fechados, da normalização de buckets (com e sem cache) e da forma do `query` — rápidos, sem rede.
- **Integração (IT):** scrape de `/actuator/prometheus` com chat real e provider fake; exporter OTLP fake capturando o payload decodificado para provar ausência de custo e presença de `usage_details`; consulta à Metrics API v2 do self-host com modelo/preço de referência do operador.
- **Integração (acesso — decisão B):** IT/MockMvc cobrindo `remoteAddr` loopback/externo no scrape, health/info sem credencial e `/actuator/env` com/sem credencial; `git diff --name-only` prova `application.properties` inalterado e único arquivo de código autorizado.
- **Caracterização/segurança:** `git diff --name-only`, `grep` de campos de custo proibidos e do nome antigo da métrica, suíte completa. Comando canônico: `npm test` (`harness.config.json.commands.suiteFull`); backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`.

## 8. Requisitos Funcionais

- **RF-01 — `chat.turns`.** Counter sem tags, exposição `chat_turns_total`, incrementado uma vez por turno no ponto instrumentado do ADR-001; nenhum incremento duplicado por retry. *(ADR Interface "Métricas Micrometer" linha 1; RF7 do PRD.)*
- **RF-02 — `chat.provider.calls`.** Counter com tags `provider`, `model`, `attempt`, exposição `chat_provider_calls_total`; conta chamadas ao provider **incluindo cada retry** (`attempt ≥ 1`). *(ADR Interface linha 2 + Consequência "retries"; RF2/RF7 do PRD.)*
- **RF-03 — `chat.provider.failures`.** Counter com tags `provider`, `model`, `reason`, exposição `chat_provider_failures_total`; `reason` é rótulo classificado do conjunto fechado, sem texto bruto. *(ADR Interface linha 3 + nota de cardinalidade; RF7 do PRD.)*
- **RF-04 — `chat.provider.latency`.** Timer com tags `provider`, `model`, exposição `chat_provider_latency_seconds` (família de latência do Micrometer); mede a chamada ao provider, não o turno inteiro. *(ADR Interface linha 4.)*
- **RF-05 — `chat.telemetry.export.failures` (RF9).** Counter com tag `sink=langfuse`, exposição `chat_telemetry_export_failures_total`; incremento no exporter do ADR-002, definição/nome canônico/exposição neste ADR. Grafia antiga `chat.telemetry.export.failures.total` retirada. *(ADR Interface linha 5 + nota "Nome canônico único"; RF9 do PRD.)*
- **RF-06 — `chat.telemetry.redaction.failures`.** Counter sem tags, exposição `chat_telemetry_redaction_failures_total`; incrementado quando o redator do ADR-003 falha fechado, sem registrar texto. *(ADR Interface linha 6; RF4 do PRD.)*
- **RF-07 — `chat.provider.tokens`.** DistributionSummary com tags `provider`, `model`, `token_type ∈ {input,output,total}`, exposição `chat_provider_tokens_sum`; permite cálculo independente de custo se o catálogo do Langfuse mudar (US-02). `input_cached_tokens` não é valor de `token_type` nesta série. *(ADR Interface linha 7 + Consequência positiva; US-02.)*
- **RF-08 — Exposição exclusiva e restrita em `/actuator/prometheus`.** As sete séries são consultáveis por `curl`/MockMvc nesse endpoint; nenhuma série é publicada por outro endpoint próprio. Acesso ao scrape exige `remoteAddr` ∈ {`127.0.0.1`, `::1`} via `WebExpressionAuthorizationManager("hasIpAddress('127.0.0.1') or hasIpAddress('::1')")` na chain `@Order(2)`; fora ⇒ 403. `/actuator/health`, `/actuator/health/**` e `/actuator/info` seguem `permitAll`; `/actuator/**` deixa de ser `permitAll` e demais caminhos caem em `authenticated()` (404 pela exposure list após autenticação). Sem credencial nova, sem dependência nova; `application.properties` inalterado; único arquivo de código autorizado: `SecurityConfig.java`. *(ADR Decisão 1 + Decisão 7 + Interface "Exposição HTTP do Actuator"; RF7 do PRD.)*
- **RF-09 — Custo inferido, zero custo enviado.** O filho `generation` leva `langfuse.observation.type=generation`, `langfuse.observation.model.name` (modelo efetivo de `ChatTrace`) e `langfuse.observation.usage_details` com buckets excludentes; backend nunca envia `langfuse.observation.cost_details`, `gen_ai.usage.cost` ou `costDetails`. `costDetails`/`totalCost` são calculados pelo Langfuse. *(ADR Decisão 3 + "Contrato de custo"; RF8 do PRD.)*
- **RF-10 — Normalização de tokens no backend.** Com cache exposto pelo provider: `input = promptTokens - cachedTokens`, `input_cached_tokens = cachedTokens` em bucket separado; sem cache: `input = promptTokens` e chave de cache ausente. Em ambos, `total` = soma dos buckets, nunca bucket duplicado. Aplicável a `langfuse.observation.usage_details` — não cria novo rótulo em `chat.provider.tokens`. *(ADR Consequência negativa — decisão do operador 2026-11-25; doc oficial §12.)*
- **RF-11 — Consulta `sum_totalCost` pela Metrics API v2.** `GET /api/public/v2/metrics?query=<JSON>` com `view=observations`, `metrics:[{measure:"totalCost",aggregation:"sum"}]`, dimensão por modelo e `orderBy:[{field:"sum_totalCost",direction:"desc"}]`; header v4 do ADR-002 garante tempo real; resposta contém `data[].sum_totalCost`. `measure`/`aggregation` são campos dentro do JSON de `query`, nunca query params soltos. *(ADR Decisão 5 + "Modelo de dados lido pelo Langfuse"; RF8 do PRD.)*
- **RF-12 — Model definition e vigência.** Quando o modelo casa com definition default da versão instalada, o Langfuse infere custo; sem match, o operador cadastra definition custom via `POST /api/public/models` com preço por ele fornecido; definição nova afeta somente generations posteriores. Backend nunca inventa preço nem cadastra definition. *(ADR Decisão 4 + Consequência negativa; doc oficial §12.)*
- **RF-13 — Instrumentação nos pontos já congelados.** As séries são registradas diretamente pelos pontos instrumentados do ADR-001 (turno e callbacks do provider), do ADR-002 (exporter) e do ADR-003 (redator); nenhum span, atributo `langfuse.*` ou série semconv é duplicado. *(ADR Decisão 1 e 6.)*

## 9. Requisitos Não-Funcionais

- **NFR-01 — Nomenclatura exata.** Pares nominal→Prometheus e tipos da tabela do ADR são imutáveis: contadores terminam em `_total`, timer em `_seconds` (família), summary em `_sum`; nomes antigos ou grafias mistas são defeito. *(ADR Interface + nota de nome canônico; Prometheus naming — §12.)*
- **NFR-02 — Cardinalidade controlada.** `reason` e `token_type` são conjuntos fechados; `sink=langfuse` é fixo; IDs (`sessionId`, `tenantId`, `turnId`, `clientId`, `promptTemplateId`), texto, prompt, resposta e stack trace são proibidos como rótulo. *(ADR nota abaixo da tabela; PRD RB1/RB2.)*
- **NFR-03 — Zero custo no payload.** Nenhum campo de custo calculado pelo backend em `src/main/`: `0` ocorrência de `langfuse.observation.cost_details`, `gen_ai.usage.cost`, `costDetails` ou literal de preço. *(ADR Decisão 3 + Alternativa descartada; RF8/RB3.)*
- **NFR-04 — Nenhum OTLP metrics/logs.** Nenhum exporter de metrics é registrado; nenhuma requisição a `/api/public/otel/v1/metrics` ou `/v1/logs`; nenhuma auto-instrumentação global acrescenta séries de infra. *(ADR Decisão 2 + Interface "Sinais OTLP"; RB5/RB6.)*
- **NFR-05 — Sem `ObservationChatModelListener`.** Nenhuma dependência da implementação `@Experimental` do tag 1.19.0 como contrato de métrica; 0 série semconv duplicada. *(ADR Decisão 6 + Alternativa descartada; §12.)*
- **NFR-06 — Flag herdada, sem novo botão.** `HIVE_TELEMETRY_ENABLED` permanece com default `false` (opt-in) e `true`=100% conforme ADR-002; este ADR não cria variável de sampling de métrica, taxa intermediária ou segundo botão de desligar. *(ADR-002 Interface "Variável de habilitação"; ADR-005 não reabre ADR-002.)*
- **NFR-07 — Rastreabilidade e fontes.** Cada `RF`/`NFR` desta SPEC vira exatamente 1 task em `TASKS.md` (23 ↔ 23; a decisão de exposição é coberta dentro de RF-08/MET-08, sem ticket novo); toda afirmação sobre API de terceiro cita URL + data da consulta. *(ADR Fontes; RESEARCH §10.)*
- **NFR-08 — Não regressão.** Contrato público do chat, cota, limites `m/c/t`, `turnId`/`clientId` e headers de rate limit intactos; suíte completa 0 falhas. Única mudança de config de segurança autorizada é a chain `@Order(2)` de `SecurityConfig.java` para o scrape (RF-08); regras de autenticação do chat, RBAC e probes permanecem intactos. Nenhuma UI nova nesta fatia ⇒ H3/frontend N/A. *(PRD RB2, §9; ADR Decisão 7.)*
- **NFR-09 — Delta e escopo de escrita.** Nenhum doc vivo (`.agents/rules/**`, `AGENTS.md`) é alterado: Delta Specs = nenhuma (PRD RB7). Neste ciclo só `SPEC.md` e `TASKS.md` deste diretório são escritos (criação original + ajuste da emenda de exposição) — sem código, branch, `VERIFY.md` ou alteração em ADR-001/002/003/004/PRD/RESEARCH/ADR-INDEX.
- **NFR-10 — Custo zero sem definition é esperado.** Sem model definition que case, `sum_totalCost` retorna 0 mesmo com usage correto; não é defeito de instrumentação e não dispara cálculo pelo backend. Preço/default atualizam com a versão instalada; definitions custom valem para generations posteriores. *(ADR Consequência negativa; doc oficial §12.)*

## 10. Critérios de Aceite (executáveis)

Servidor OTLP fake captura e decodifica o payload no IT; o scrape é feito com `curl -s http://localhost:8080/actuator/prometheus` (loopback ⇒ 200) e a negação é provada em IT/MockMvc com `remoteAddr` externo (⇒ 403); a consulta de custo usa `curl -G --data-urlencode 'query=<JSON>'` com Basic de leitura vindo de variável de ambiente (nenhum literal de credencial no comando). Comando canônico: `npm test`; backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`.

| Req | Comando / verificação | Asserção observável |
|---|---|---|
| RF-01 | `curl -s localhost:8080/actuator/prometheus \| grep '^chat_turns_total'` + IT de 1 turno com 2 tentativas | `chat_turns_total` incrementa exatamente +1 por turno; 0 tag; 0 incremento extra por retry |
| RF-02 | mesmo scrape + IT com provider fake de 2 tentativas | `chat_provider_calls_total{provider="…",model="…",attempt="1"}` e `attempt="2"`; soma = 2; 0 valor de `attempt` fora de `≥1` |
| RF-03 | mesmo scrape + `mvn -f backend/pom.xml test -Dtest=ChatMetricsContractTest` | `chat_provider_failures_total{provider,model,reason}` incrementa só na tentativa falha; `reason` ∈ conjunto fechado; 0 `at br.com` e 0 texto de prompt |
| RF-04 | mesmo scrape + caracterização de latência | família `chat_provider_latency_seconds` presente com tags `provider`,`model`; duração > 0 e < orçamento caracterizado; 0 medição do turno inteiro |
| RF-05 | IT com exporter recusando conexão + scrape | `chat_telemetry_export_failures_total{sink="langfuse"}` +1 por lote falho; nome lógico `chat.telemetry.export.failures` no Micrometer; `grep -rn "chat.telemetry.export.failures.total" backend/*/src/main` ⇒ 0 |
| RF-06 | IT com redator forçado a falhar (ADR-003) + scrape | `chat_telemetry_redaction_failures_total` +1; 0 tag; 0 `langfuse.observation.input`/`output` no payload; metadados preservados |
| RF-07 | mesmo scrape + IT com 3 chamadas | `chat_provider_tokens_sum{...,token_type="input"}`, `="output"`, `="total"` presentes; conjunto de `token_type` ⊆ `{input,output,total}`; 0 `token_type="input_cached_tokens"` |
| RF-08 | `curl -s localhost:8080/actuator/prometheus` + IT/MockMvc com `remoteAddr` `127.0.0.1`, `::1` e `203.0.113.10` + `curl` em health/info/env | 200 com as 7 séries em loopback (IPv4/IPv6); 403 com `{"error": "Acesso negado"}` para IP externo; `/actuator/health`, `/actuator/health/**` e `/actuator/info` ⇒ 200 sem credencial; `/actuator/env` ⇒ 404 com usuário autenticado (401/403 sem credencial); 0 endpoint próprio de métricas; 0 diff em `application.properties` |
| RF-09 | IT `LangfuseCostDerivationIT` com payload decodificado | `langfuse.observation.type=generation`, `langfuse.observation.model.name` = modelo efetivo, `usage_details` com buckets excludentes; 0 campo de custo no payload |
| RF-10 | mesmo IT com fixture 17903/17817/188/18091 e com fixture sem cache | `usage_details` == `{"input":86,"output":188,"input_cached_tokens":17817,"total":18091}`; sem cache == `{"input":p,"output":c,"total":p+c}` sem chave de cache; nenhum token em 2 buckets |
| RF-11 | `curl -G --data-urlencode 'query={"view":"observations","metrics":[{"measure":"totalCost","aggregation":"sum"}],"dimensions":[{"field":"providedModelName"}],"filters":[],"orderBy":[{"field":"sum_totalCost","direction":"desc"}]}' "$LANGFUSE_BASE_URL/api/public/v2/metrics"` | resposta `data[]` com `sum_totalCost > 0` para modelo/preço de referência; `orderBy[0].field=sum_totalCost`; 0 `?measure=`/`?aggregation=` soltos; header v4 presente no OTLP |
| RF-12 | IT com modelo sem definition, depois `POST /api/public/models` com preço do operador, nova generation | antes: `sum_totalCost=0`; depois: generation posterior > 0 com preço fornecido; generation anterior permanece 0; 0 preço inventado no código |
| RF-13 | varredura de pontos de instrumentação + `git diff --name-only` | séries incrementadas só em turno/callback/exporter/redator; 0 span e 0 chave `langfuse.*` nova; 0 diff em ADR-001/002/003/004 |
| NFR-01 | teste de caracterização de strings | conjunto exato dos 14 nomes congelados (7 lógicos + 7 exposição), tipos e sufixos; 0 grafia antiga |
| NFR-02 | varredura de tags no scrape + teste de caracterização | conjuntos de tags por série idênticos ao ADR; 0 ID/texto/prompt/`sk-` em rótulo |
| NFR-03 | `grep -rn "cost_details\|gen_ai.usage.cost\|costDetails" backend/*/src/main/` | 0 ocorrência; payload decodificado sem campo de custo |
| NFR-04 | varredura de requisições do IT + inspeção de beans | 0 `/v1/metrics` e 0 `/v1/logs`; 0 exporter de metrics; 0 auto-instrumentação global |
| NFR-05 | `grep -rn "ObservationChatModelListener" backend/*/src/main/` | 0 ocorrência; 0 série semconv duplicada |
| NFR-06 | auditoria de config + caracterização herdada de ADR-002 | `HIVE_TELEMETRY_ENABLED` default `false`, `true`=100%; 0 nova env de métrica, 0 taxa intermediária |
| NFR-07 | `grep -c '^### MET-' TASKS.md` + auditoria de URLs | 23 seções ↔ 23 RF/NFR (13 RF + 10 NFR), 0 órfão; toda URL de terceiro com data |
| NFR-08 | `npm test` + `mvn -f backend/pom.xml -Pintegration verify` + `git diff --name-only` | 0 falhas; 0 diff em contrato/cota/limites/RBAC/regras de autenticação do chat; diff permitido só na chain `@Order(2)` de `SecurityConfig.java` para `/actuator/prometheus`; H3 N/A (sem UI) |
| NFR-09 | `git status --short` + `grep` em `.agents/rules/` | só `SPEC.md`/`TASKS.md` deste diretório escritos; 0 diff em doc vivo e em ADRs 001–004 |
| NFR-10 | IT sem model definition + IT com definition nova | `sum_totalCost=0` sem match, com usage correto; 0 chamada ao backend para calcular preço; definição nova não altera generation anterior |

## 11. Riscos

| Risco | Mitigação | Origem no ADR / fonte |
|---|---|---|
| 🟠 `usage_details` flat não é normalizado pelo Langfuse; cache inclusivo superestima custo | RF-10 normaliza no backend antes do export; IT com fixture 17903/17817/188/18091 (INV-5) | ADR Consequência negativa + decisão operador 2026-11-25; doc oficial §12 |
| 🟠 `sum_totalCost=0` sem model definition pode ser lido como falha de instrumentação | NFR-10/RF-12 definem comportamento esperado e cadastro pelo operador; preço nunca inventado | ADR Consequência negativa; doc oficial §12 |
| 🟡 Divergência de nome entre Micrometer e Prometheus (grafia antiga `.total`) | INV-10 + NFR-01 caracterizam o par canônico e proíbem a grafia retirada | ADR nota "Nome canônico único"; Prometheus naming — §12 |
| 🟡 Rótulo de alta cardinalidade vaza ID/texto e infla série | INV-3/INV-4 + NFR-02 varrem tags no scrape | ADR nota abaixo da tabela; PRD RB1 |
| Preços default mudam só com upgrade da instância | registrado como consequência; definition custom via operador (RF-12), sem catálogo no código | ADR Consequência negativa; doc oficial §12 |
| Header v4 ausente atrasa APIs v2 em até 15 min | RF-11 exige header v4 do ADR-002; nenhum "esperar 15 min" inventado no teste | ADR Decisão 5; Metrics API — §12 |
| `ObservationChatModelListener` `@Experimental` reintroduzido como métrica | NFR-05 com `grep` e 0 série semconv duplicada | ADR Decisão 6; tag 1.19.0 — §12 |
| Correlação entre Prometheus e Langfuse por tempo/atributos, não por ID | registrado como consequência negativa; nenhuma chave compartilhada inventada | ADR Consequências |
| 🟠 scrape anônimo expõe volume/latência/provider fora da loopback | RF-08/INV-11 + MET-08 provam 200 só em loopback e 403 com IP externo; health/info intactos; único arquivo de código autorizado `SecurityConfig.java` | ADR Decisão 7 + Alternativa "manter `/actuator/**` `permitAll`"; Spring Boot Endpoints §Security — §12 |
| 🟡 coletor Prometheus em outro host deixa de alcançar o scrape | registrado como limite da decisão B; nova decisão (CIDR explícito ou Basic) antes de implantar, sem "config para depois" em código | ADR Consequência negativa — 2026-11-25 |

**Fora de escopo desta SPEC:** contrato de spans/correlação (ADR-001); transporte/header/exporter/flag (ADR-002); redação e texto de prompt/resposta (ADR-003); proxy/RBAC/retention (ADR-004); código, branch, `VERIFY.md`, merge e `workflow-state.json`.

## 12. Fontes

Consultadas em 2026-11-25. Nenhuma fonte de produto foi inventada; decisões permanecem as do `ADR.md`.

- ADR-005 — `ADR.md` deste diretório (Decisão 1–6, Modelo de dados lido pelo Langfuse, Interface congelada, notas de nome canônico, Alternativas, Consequências, Fontes) — 2026-11-25.
- PRD aprovado — `.spec/prd/telemetria-ia-langfuse/PRD.md` §5 (RF4/RF7/RF8/RF9), §6 (RB1/RB2/RB3/RB5/RB6), §9, §10 (US-02), §11, §16 — 2026-11-25.
- RESEARCH — eixo D5, §6, §7 e §10 (S9/S10/S14/S15) — 2026-11-25.
- ADR-002 — Interface congelada (header v4, flag `HIVE_TELEMETRY_ENABLED` default `false`, nome canônico da métrica de falha) — 2026-11-25.
- Architect — decisão de 2026-11-25 sobre nome canônico `chat.telemetry.export.failures` → `chat_telemetry_export_failures_total` (registrada no ADR §Interface congelada); nota Brain `adr-002-005-conflitos-encoding-metrica-flag-2026-11-25` — 2026-11-25.
- Architect — emenda de exposição de 2026-11-25 (decisão B): `ADR.md` Decisão 7 + "Exposição HTTP do Actuator" + "Impacto em MET-08"; nota Brain `arquitetura/projetos/hive/adr-005-exposicao-prometheus-20261125` — 2026-11-25.
- Spring Security 7.0 — `WebSecurityExpressionRoot.hasIpAddress(String)` (aceita IP ou faixa IP/Netmask) — https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/web/access/expression/WebSecurityExpressionRoot.html — 2026-11-25 (reconsultado 2026-11-25).
- Spring Security — `WebExpressionAuthorizationManager(String expressionString)` (construtor desde 5.8; `AuthorizationManager<RequestAuthorizationContext>`) — https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/access/expression/WebExpressionAuthorizationManager.html — 2026-11-25 (reconsultado 2026-11-25).
- Spring Boot — Actuator metrics/Micrometer — https://docs.spring.io/spring-boot/reference/actuator/metrics.html — 2026-11-25.
- Spring Boot — endpoint `/actuator/prometheus` — https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html — 2026-11-25.
- Langfuse — Token & Cost Tracking (generation/embedding, custo inferido, prioridade de custo ingerido, `usage_details` como buckets excludentes, normalização, preços no self-host, `POST /api/public/models`) — https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25.
- Langfuse — Metrics API v2 (`GET /api/public/v2/metrics`, parâmetro JSON `query`, `view=observations`, `metrics[].measure/aggregation`, `dimensions`, `orderBy.field=sum_totalCost`, `data[].sum_totalCost`, atraso sem header v4) — https://langfuse.com/docs/metrics/features/metrics-api — 2026-11-25.
- Langfuse — FAQ OTEL (metrics aceitas e descartadas) — https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25.
- LangChain4j — `ObservationChatModelListener` `@Experimental` no tag 1.19.0 — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25.
- Prometheus — Metric and label naming (sufixo `total`, caracteres válidos) — https://prometheus.io/docs/practices/naming/ — 2026-11-25.
- Fatos locais (leitura em 2026-11-25): `backend/dependencies/pom.xml:25,114` (Spring Boot 4.1.0 + Actuator), `backend/bootstrap/src/main/resources/application.properties:27-28` (`health,info,prometheus`, `show-values=never` — inalterado), `backend/security/impl/src/main/java/br/com/systec/hive/security/impl/config/SecurityConfig.java:84` (`/actuator/**` em `permitAll` na chain `@Order(2)`; `accessDeniedHandler` com `{"error": "Acesso negado"}`), `proxy/langfuse-proxy.conf.template:3,37-41` (proxy cobre só Langfuse), `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTrace.java:15,45-64`, `backend/assistant/impl/src/main/java/br/com/systec/hive/assistant/impl/diagnostics/FailureClassifier.java:42`, `harness.config.json:13-16`.

**Delta Specs:** nenhuma. ADR-005 não altera doc vivo (`.agents/rules/**`, `AGENTS.md` — PRD RB7). Configuração da aplicação é código/config do ADR, não doc vivo.

## 13. Score do portão (spec-gate)

| Item | Veredito |
|---|---|
| ADRs (decisão + "Porquê") | ✅ §1 |
| DDD Modular (api/impl/bootstrap) | ✅ §2/§6 |
| Segurança/IAM (autenticação do destino, escopo, sem `@HasPermission` novo; regra de IP explícita na chain `@Order(2)`) | ✅ §3/§4 |
| ERD | N/A — sem entidade nova neste ciclo |
| Event Registry | N/A — nenhum evento de domínio novo |
| Migrations | N/A — sem migration Java neste ciclo |
| Invariantes de entidade (séries/observação/acesso) | ✅ §7.1 (INV-1..INV-11) |
| Invariantes de workflow | ✅ §7.2 |
| Test Strategy | ✅ §7.4 |
| Single File | ✅ este arquivo |
| Domínio claro | ✅ `assistant` — observabilidade operacional e de custo |

`itens aplicáveis = 8` (11 − 3 N/A com motivo) · `atendidos = 8` · **score = 100%**.
