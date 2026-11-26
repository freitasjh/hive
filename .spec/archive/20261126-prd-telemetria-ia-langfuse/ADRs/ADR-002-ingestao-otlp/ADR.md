---
prd_id: telemetria-ia-langfuse
adr_id: ADR-002
title: "Ingestão OTLP/HTTP no Langfuse self-host v4 (endpoint, Basic pk:sk, header v4, isolamento do exporter)"
status: Proposto
data: 2026-11-25
depende-de: ADR-001
rf: "RF1, RF9"
type: adr
---

# ADR-002 — Ingestão OTLP/HTTP no Langfuse self-host v4

- **Data:** 2026-11-25
- **Status:** Proposto — decisão congelada; sem merge (`ADR-INDEX.md`: `todo`).
- **depende-de:** ADR-001 (contrato de spans e atributos propagados).
- **RFs atendidos:** RF9 (falha não altera resposta), RF1 (root obrigatório chega ao Langfuse).

## Contexto

O backend é Java/Spring Boot 4.1.0 com LangChain4j 1.19.0 (`backend/dependencies/pom.xml:25,37,114`). Langfuse self-host v4 Community recebe OTLP por HTTP (JSON e protobuf); **gRPC não é suportado**; o endpoint legado `/api/public/ingestion` está deprecado e, no self-host v4 em modo `events_only`, indisponível. Sem o header de versão, dados OTLP diretos podem levar até 15 min nas APIs v2.

## Decisão

1. **Transporte:** `POST` OTLP/**HTTP-protobuf** traces para `/api/public/otel/v1/traces` na instância self-host v4, com TLS obrigatório em produção. `Content-Type: application/x-protobuf`: é o que o `OtlpHttpSpanExporter` do SDK Java emite; `http/json` não é implementado pelo SDK Java e os mantenedores confirmaram que não planejam implementá-lo (issue #5598, 2023-07-06, consultado 2026-11-25). O Langfuse aceita OTLP/HTTP com JSON **e** protobuf (2026-11-25), então protobuf é compatível sem exporter próprio. Canário de redação do ADR-003 passa a inspecionar payload **decodificado**, não JSON cru.
2. **Autenticação:** `Authorization: Basic base64(pk:sk)`, chave de projeto com permissão **`Application`** (enviar traces). `pk`/`sk` vêm de cofre/secret; nunca de literal de repo, log, payload ou resposta (RB3).
3. **Header v4 obrigatório:** `x-langfuse-ingestion-version: 4` em toda requisição — condição de ingestão em tempo real no modelo v4 e nas APIs Observations/Metrics v2.
4. **Exporter isolado e best-effort:** instância/processador de export dedicada, alimentada **somente** pelos dois instrumentos do ADR-001. Sem registro global de auto-instrumentação, sem OTLP metrics e sem OTLP logs. Envio assíncrono em batch, timeout limitado, sem retry da chamada de chat e sem propagação de exceção para o fluxo de resposta; falha incrementa `chat.telemetry.export.failures` (exposição Prometheus `chat_telemetry_export_failures_total`, ADR-005) e vira log sem texto bruto.
5. **Sampling e default da flag:** `HIVE_TELEMETRY_ENABLED=true` ⇒ 100% dos turnos (`always-on`); `false` ⇒ nenhum span emitido e nenhum export (`always-off`). **Default = `false` (opt-in)**: sem egresso de prompt/resposta, sem exigir `pk`/`sk` em dev/test e coerente com RF9 (falha de telemetria não muda o chat). Em produção a flag é ligada **explicitamente** por configuração, com endpoint e credenciais vindos de secret. Confiar no sampler default do Spring Boot (10%) violaria o requisito de 100% (Spring Boot tracing — https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html — 2026-11-25). Único botão de desligar na primeira fatia; sem taxa intermediária.
6. **Root obrigatório por lote:** cada lote exportado contém o root `chat.turn` com os atributos de correlação replicados conforme ADR-001 — o Langfuse exige root para criar o trace e filtra/agrega por observation.

### Blueprint de contrato (API de ingestão)

| Elemento | Contrato |
|---|---|
| Método / path | `POST /api/public/otel/v1/traces` |
| Transporte | OTLP/HTTP, `Content-Type: application/x-protobuf` |
| Auth | `Authorization: Basic <base64(pk:sk)>`, chave `Application` |
| Header v4 | `x-langfuse-ingestion-version: 4` |
| Transporte TLS | obrigatório fora de localhost |
| Resposta | falha de ingestão **nunca** altera status/contrato do chat (RF9) |

## Interface congelada

| Nome congelado | Valor fixado (downstream não altera) |
|---|---|
| Path do endpoint | `/api/public/otel/v1/traces` |
| Header de versão | `x-langfuse-ingestion-version: 4` |
| Esquema de auth | Basic `pk:sk`, escopo `Application` (escrita) |
| Encoding | OTLP/HTTP **protobuf** (`Content-Type: application/x-protobuf`) |
| Variável de habilitação | `HIVE_TELEMETRY_ENABLED` — default `false`; `true` = 100% (`always-on`), `false` = 0% (`always-off`); produção liga explicitamente |
| Variáveis de credencial | `LANGFUSE_INGESTION_PK`, `LANGFUSE_INGESTION_SK` — nomes de referência a secret; valores nunca em repo/log/payload |
| Variável de endpoint | `LANGFUSE_OTLP_TRACES_ENDPOINT` — URL completa terminando no path acima |
| Sinais exportados | **traces somente**; nenhum OTLP metrics/logs |
| Métrica de falha de export | `chat.telemetry.export.failures` → `chat_telemetry_export_failures_total` (nome canônico em ADR-005) |

### Ajustes de 2026-11-25 na Interface congelada (decisão architect — 3 conflitos SPEC/TASKS)

| Item | Antes | Depois | Fonte (URL + data) |
|---|---|---|---|
| Encoding | `OTLP/HTTP JSON` | `OTLP/HTTP protobuf` (`application/x-protobuf`) | https://github.com/open-telemetry/opentelemetry-java/issues/5598 — 2023-07-06 (consultado 2026-11-25); https://langfuse.com/integrations/native/opentelemetry — 2026-11-25 |
| Métrica de falha | `chat.telemetry.export.failures.total` | `chat_telemetry_export_failures_total` (Micrometer `chat.telemetry.export.failures`) | https://prometheus.io/docs/practices/naming/ — 2026-11-25 |
| Flag `HIVE_TELEMETRY_ENABLED` | sem default definido | `false` por default (opt-in); produção liga explicitamente | https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html — 2026-11-25 |

Nenhuma outra linha da Interface congelada mudou: path, header v4, auth, credenciais, endpoint e sinais permanecem idênticos.

## Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| OpenTelemetry Collector no meio (batch/filter/mask) | Componente operacional extra; masking no Collector ocorre **depois** de o dado sair da aplicação (contraria RF3); filtrar spans pode deixar trace sem root. Fonte: docs OTEL e masking — 2026-11-25. |
| SDK Langfuse Python/JS | Documentação recomenda SDK só para Python/JS; destino é Java/Spring Boot 4.1.0. Fonte: OTEL overview — 2026-11-25. |
| Endpoint legado `/api/public/ingestion` | Deprecado; sunset em 2026-11-16 e indisponível no self-host v4 em `events_only`. Fonte: Public API — 2026-11-25. |
| OTLP/gRPC | Langfuse não suporta gRPC. Fonte: OTEL overview — 2026-11-25. |
| Registro global de auto-instrumentação OTel | Traz spans de HTTP/SQL/infra, violando RB6 e inflando custo de observação. |
| OTLP metrics ao Langfuse | Endpoint aceita e **descarta** métricas; RF7 seria impossível. Fonte: FAQ OTEL — 2026-11-25. |

## Consequências

**Positivas**
- Formato aberto, sem SDK proprietário Java; troca de backend é mudança de endpoint/credencial, não de instrumentação.
- Header v4 dá disponibilidade em tempo real nas APIs v2 usadas por RF1/RF8.
- Exporter isolado + best-effort torna RF9 diretamente testável (exporter fora → chat 200 e contador incrementado).

**Negativas**
- Atributos `langfuse.*` são montados à mão (ADR-001); nenhum helper de SDK Java.
- Export é assíncrono: pequena janela entre o turno e a aparição do trace, mesmo com header v4.
- Com protobuf, o canário de redação (ADR-003/RED-11) não lê JSON cru: precisa decodificar OTLP ou validar pela Observations API v2 — custo de teste maior que o canário JSON original.
- Rotação de `sk` é manual (criar nova chave, trocar secret, revogar antiga); chave antiga não pode ser recuperada.

### Desvios de implementação registrados em 2026-11-25 (revisão pós-código; sem nova decisão arquitetural)

1. **`spring-boot-starter-opentelemetry` não foi usado.** O starter traria `micrometer-registry-otlp` (metrics/logs), violando "traces somente". Usado `opentelemetry-exporter-otlp:1.62.0` direto. Nuance registrada pelo review: esse artefato é agregador e contém classes de exporter de metrics/logs, mas sem bridge/starter OTel e sem `management.*otlp.*.endpoint` não há egresso extra (`mvn dependency:tree`; docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html — 2026-11-25).
2. **`spring-boot-starter-actuator` + `micrometer-registry-prometheus` adicionados** — não existiam no pom; sem `MeterRegistry` o bean do sink falhava. Dono do endpoint `/actuator/prometheus` é o ADR-005.
3. **Decode protobuf fixado em `1.10.0-alpha` no módulo de IT** — `1.11.1-alpha` gera protobuf 4.36.2 contra runtime 4.34.2 (`RuntimeVersionException`). Escopo de teste, wire format compatível; sem efeito em produção.
4. **IDs do ADR-001 remapeados para novos IDs OTel** — hierarquia (`parentSpanId`/`traceId`), chaves `langfuse.*`, nomes e timestamps preservados e provados em IT. Impacto em `RED-11` (ADR-003) registrado e aceito em `TASKS.md:8`.
5. **`enabled=true` sem endpoint/credenciais** — noop + warn **e** incremento de `chat.telemetry.export.failures` (correção do review); telemetria perdida com sinal, nunca erro de chat.
6. **Headers Basic e `x-langfuse-ingestion-version` em código, não em properties** — consequência direta do desvio 1. Valores congelados intactos e provados em IT.

> Risco fora deste escopo, encaminhado ao ADR-005: `SecurityConfig.java:84` faz `/actuator/**` `permitAll` e `application.properties` expõe `prometheus` ⇒ scrape público sem autenticação. Não altera contrato do chat nem credenciais; decisão de exposição cabe ao ADR-005.

## Fontes

- Langfuse — OpenTelemetry integration (endpoint `/api/public/otel/v1/traces`, Basic `pk:sk`, header v4, JSON/protobuf, sem gRPC, propagação de atributos) — https://langfuse.com/integrations/native/opentelemetry — 2026-11-25.
- Langfuse — migração v4 — https://langfuse.com/integrations/native/opentelemetry/migration-to-v4 — 2026-11-24.
- Langfuse — compatibilidade/header v4 — https://langfuse.com/docs/compatibility — 2026-11-24.
- Langfuse — FAQ OTEL (somente traces) — https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25.
- Langfuse — Public API (depreciação do endpoint legado, escopo de chaves) — https://langfuse.com/docs/api-and-data-platform/features/public-api — 2026-11-25.
- OpenTelemetry Java — issue #5598 (JSON exporter não implementado; mantenedores confirmam que não planejam; `Content-Type` permanece `application/x-protobuf`) — https://github.com/open-telemetry/opentelemetry-java/issues/5598 — 2023-07-06 (consultado 2026-11-25).
- Spring Boot — Tracing (samplers `always-on`/`always-off`; sampler default 10%; `OtlpHttpSpanExporterBuilderCustomizer`) — https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html — 2026-11-25.
- Prometheus — Metric and label naming (caracteres válidos; sufixo `total` para contadores) — https://prometheus.io/docs/practices/naming/ — 2026-11-25.
- RESEARCH — eixo D1 e §7 — 2026-11-25.
