<!-- scope: adr/ADR-002 -->
# SPEC — ADR-002: Ingestão OTLP/HTTP no Langfuse self-host v4 (endpoint, Basic `pk:sk`, header v4, isolamento do exporter)

> **Escopo:** somente a decisão congelada no `ADR.md` deste diretório. Cada `RF`/`NFR` abaixo aponta para a cláusula do ADR que o origina. Não reespecifica o modelo de spans/correlação (ADR-001), a redação de texto (ADR-003), proxy/RBAC/retention (ADR-004) nem a definição/exposição completa das métricas de negócio (ADR-005).
>
> **Rastreabilidade:** `ADR.md` (decisão + interface congelada) → esta SPEC → `TASKS.md` → evidência no `VERIFY.md` pós-merge. `depende-de: ADR-001`. Status do ADR: Proposto (sem merge).
>
> **Deepsearch (2026-11-25):** propriedades Spring Boot 4.1, o encoding emitido pelo `OtlpHttpSpanExporter` do SDK Java e a convenção de nomes do Prometheus foram verificados em doc oficial/código-fonte — §12. As três divergências que bloqueavam a versão anterior desta SPEC (encoding, nome da métrica, default da flag) foram decididas pelo `architect` em 2026-11-25 e registradas no `ADR.md` §"Ajustes de 2026-11-25"; esta SPEC aplica essas decisões sem reescrever o ADR.

## 1. ADRs

### ADR-002 (este ciclo)

- **Decision:** `POST` OTLP/**HTTP-protobuf** de traces para `/api/public/otel/v1/traces` na instância self-host v4, com `Content-Type: application/x-protobuf` (encoding emitido pelo `OtlpHttpSpanExporter` do SDK Java; `http/json` não é implementado); `Authorization: Basic base64(pk:sk)` com chave de projeto de escopo `Application`; `x-langfuse-ingestion-version: 4` em toda requisição; exporter isolado e best-effort (sem registro global de auto-instrumentação, sem OTLP metrics/logs), assíncrono em batch, com timeout limitado, sem retry da chamada de chat e sem propagar exceção ao fluxo de resposta; `HIVE_TELEMETRY_ENABLED` é o único botão, com **default `false` (opt-in)**: `true` ⇒ 100% (`always-on`), `false` ⇒ 0% (`always-off`); o sampler default do Spring Boot (10%, `parent-based-trace-id-ratio`) não é usado; todo lote contém o root `chat.turn` com a correlação do ADR-001. Falha de export incrementa `chat.telemetry.export.failures` (nome lógico Micrometer), exposto em Prometheus como `chat_telemetry_export_failures_total` (definição em ADR-005), e gera log sem texto bruto. (ADR Decisão 1–6, Blueprint de contrato, Interface congelada, Ajustes de 2026-11-25)
- **Context:** Spring Boot 4.1.0 + Actuator e LangChain4j 1.19.0 (`backend/dependencies/pom.xml:25,37,114`); Langfuse self-host v4 Community aceita OTLP por HTTP (JSON e protobuf), não aceita gRPC, e o endpoint legado `/api/public/ingestion` está deprecado/indisponível em `events_only`. Sem o header v4, OTLP direto pode levar até 15 min nas APIs v2. O SDK Java emite protobuf e não implementa `http/json` (issue #5598, 2023-07-06, consultado 2026-11-25), tornando protobuf o encoding compatível sem exporter próprio. ADR-001 já congela os dois instrumentos que alimentam o export. (ADR Contexto; RESEARCH §1; §12)
- **Trade-offs:** formato aberto e sem exporter próprio (protobuf já é o que o SDK emite) versus canário de redação do ADR-003 que precisa decodificar o payload ou validar pela Observations API v2 — custo de teste maior que o canário JSON original; exporter isolado torna RF9 diretamente testável versus janela assíncrona até o trace aparecer. Collector, SDK Python/JS, endpoint legado, gRPC, auto-instrumentação global e OTLP metrics foram descartados no ADR — esta SPEC não os reabre. (ADR Decisão 1/4, Alternativas descartadas, Consequências)

ADRs afetados: **ADR-001** (`depende-de` — fornece root/filhos e as chaves `langfuse.*`); **ADR-003** (consome o payload protobuf decodificado — ou a Observations API v2 — para o canário de redação; `RED-11` precisa ser ajustado no ciclo daquele ADR, fora desta SPEC); **ADR-005** (define/exibe `chat_telemetry_export_failures_total` a partir do lógico `chat.telemetry.export.failures`; ADR-002 só incrementa); **ADR-004** (consome os traces ingestados, sem alterar este contrato).

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `assistant` — saída de telemetria de turno. O export é uma porta de infraestrutura do contexto, não regra de domínio; nenhuma dependência nova entre módulos de negócio.
- **System Type:** backend Spring Boot 4.1.0 + Actuator; configuração de exporter em `bootstrap` (`backend/bootstrap/src/main/java/br/com/systec/hive/config/`, `backend/bootstrap/src/main/resources/application*.properties`), instrumentação em `assistant/impl` (ADR-001, somente leitura aqui). Propriedades oficiais: `management.opentelemetry.tracing.export.otlp.*` e `management.opentelemetry.tracing.sampler` (§12).
- **Tenant Provisioning:** N/A — nenhum tenant, projeto Langfuse, sessão ou credencial é criado por este ADR; `tenantId` é apenas lido do dado do ADR-001.
- **Tenant Lifecycle:** N/A — inalterado; este ADR não toca ciclo de tenant, RBAC da aplicação nem provisionamento (PRD RB2/RB7).

## 3. Identity & Access Management

- **IdP / Protocol:** inalterados. Autenticação do destino é a do Langfuse self-host; nenhum fluxo de login da aplicação é modificado.
- **RBAC:** nenhuma permissão nova e nenhum `@HasPermission` novo. A chave de ingestão tem escopo **`Application` (escrita de traces)**; leitura é de outro sujeito, decidida no ADR-004 (`Viewer`/proxy) — credencial de ingestão não serve para leitura (PRD RF5/RB3).
- **MFA:** N/A — proibido tocar o ciclo de MFA (PRD §2, fora de escopo).
- **Sessão/cliente:** `sessionId`, `tenantId`, `turnId`, `clientId`, `promptTemplateId` são lidos do contrato do ADR-001, nunca gerados, reescritos ou inventados aqui.

## 4. Security, Key Management & Disaster Recovery

- **Chaves:** `pk`/`sk` vêm de cofre/secret via `LANGFUSE_INGESTION_PK` e `LANGFUSE_INGESTION_SK` — são **nomes de referência**; valores nunca em literal de repo, log, payload OTLP, atributo de span, métrica ou resposta (ADR Decisão 2, Interface congelada; PRD RB3). O `Authorization: Basic <base64(pk:sk)>` é o único portador na wire. Actuator `env`/`configprops` permanecem com `show-values=never` (padrão verificado em §12) para não expor o valor.
- **Texto:** nenhum prompt/resposta é introduzido por este ADR; o portador de texto é o ADR-003. Log de falha de export não contém texto bruto, não contém `sk-` e não contém o base64 da credencial.
- **Transporte:** TLS obrigatório fora de localhost (ADR Decisão 1, Blueprint "Transporte TLS"); `LANGFUSE_OTLP_TRACES_ENDPOINT` é a URL completa terminando em `/api/public/otel/v1/traces`.
- **Rotação:** manual — criar nova chave, trocar secret, revogar a antiga; chave antiga não é recuperável (ADR Consequência negativa). Nenhuma rotação automática é inventada aqui.
- **RTO/RPO:** N/A — nenhum estado novo persistido pela aplicação; telemetria é best-effort e a disponibilidade do chat não depende do destino (PRD RB2).

## 5. Data Model & Concurrency

- Nenhuma entidade, tabela, migration Flyway/Liquibase ou chave Redis é criada ou alterada. O lote OTLP é montado dos spans do ADR-001; traces vivem no Langfuse (PRD §11).
- **ERD:** N/A — sem entidade nova neste ciclo (o artefato é o payload OTLP, não tabela).
- **Migrations:** N/A — sem migration Java neste ciclo.
- **Concorrência:** envio assíncrono em batch com fila/timeout limitados; nenhum lock, transação ou broker novo. Estado de correlação continua restrito ao escopo de turno do ADR-001 — nenhum atributo de um turno pode entrar em lote de outro.

## 6. Event Model, Integrations & Messaging Isolation

- **Event Registry:** N/A — nenhum evento de domínio novo. Spans OTLP não são evento de domínio; a integração é saída HTTP direta.
- **Integração de saída:** `POST /api/public/otel/v1/traces` no self-host v4, alimentada **somente** pelos dois instrumentos do ADR-001. Nenhum registro global de auto-instrumentação, nenhum OTLP metrics e nenhum OTLP logs (ADR Decisão 4).
- **Isolamento:** sem broker/tópico — isolamento é por configuração do exporter dedicado e por escopo de turno. Nenhum dado de telemetria de um turno entra no lote de outro; nenhuma credencial de leitura é reutilizada para escrita.

## 7. Invariants & Property-Based Testing

### 7.1 Invariantes do lote/requisição exportada

| ID | Propriedade | Verificação |
|---|---|---|
| INV-1 | Toda requisição ao destino é `POST`, path exatamente `/api/public/otel/v1/traces`, `Content-Type: application/x-protobuf`, corpo binário decodificável como `ExportTraceServiceRequest` com `resourceSpans` | captura da requisição no servidor OTLP fake do IT + decode do corpo |
| INV-2 | `x-langfuse-ingestion-version: 4` presente e igual a `4` em **100%** das requisições | asserção sobre toda requisição capturada (0 exceção) |
| INV-3 | `Authorization` é `Basic base64(pk:sk)` com os valores das variáveis de secret; 0 ocorrência do valor em corpo, log, atributo de span ou resposta | decode do header no IT + `grep` no repositório/captura |
| INV-4 | Todo lote exportado contém ≥1 span `chat.turn` com `isRootObservation=true` e a correlação do ADR-001 replicada | decode de cada lote capturado; 0 lote sem root |
| INV-5 | `HIVE_TELEMETRY_ENABLED` ausente ou `false` ⇒ `always-off` ⇒ 0 span emitido e 0 requisição; `true` ⇒ `always-on` ⇒ 100% dos turnos exportados; sampler default 10% do Spring Boot não configurado | IT paramétrico com três casos: default-ausente, `false`, `true` |
| INV-6 | Falha de export ⇒ resposta de chat 200/contrato inalterado, 0 retry do chat, 0 exceção na thread do chat, contador +1 | IT com exporter indisponível + scrape do contador |
| INV-7 | Sinais exportados são somente traces: 0 requisição a `/v1/metrics` ou `/v1/logs`, 0 registro global de auto-instrumentação | captura de todas as requisições + inspeção de beans/config |
| INV-8 | Interface congelada imutável: path, header v4, esquema Basic/escopo, encoding protobuf, nomes de variáveis e nome canônico `chat_telemetry_export_failures_total` (lógico `chat.telemetry.export.failures`) | teste de caracterização de strings |

### 7.2 Invariantes de workflow

- **Habilitação:** `HIVE_TELEMETRY_ENABLED` default `false` (opt-in). `true` liga emissão **e** export em 100% (`always-on`); `false` desliga emissão **e** export em 0% (`always-off`). Não existe taxa intermediária, segundo botão ou sampler default 10% (ADR Decisão 5; §12).
- **Lote:** o root é condição de existência do trace no Langfuse; um lote sem root é defeito de configuração do exporter, nunca "retry" do chat (ADR Decisão 6).
- **Falha:** indisponibilidade/timeout do destino terminam no contador de falha e em log seguro; a resposta ao usuário não é reavaliada, reemitida ou retardada pela falha de telemetria (ADR Decisão 4; PRD RF9).

### 7.3 Restrições de geração de dados de teste

| Campo | Tipo | Restrição | Objetivo |
|---|---|---|---|
| `LANGFUSE_OTLP_TRACES_ENDPOINT` | URL | `https` fora de localhost; termina em `/api/public/otel/v1/traces`; sem query/fragmento | INV-1, INV-8 |
| `LANGFUSE_INGESTION_PK` / `LANGFUSE_INGESTION_SK` | String (secret) | fornecidos por fake de secret; nunca literais no repo | INV-3 |
| `Content-Type` | String | exatamente `application/x-protobuf` | INV-1, INV-8 |
| `HIVE_TELEMETRY_ENABLED` | boolean | ausente ou `false` ⇒ `always-off`; `true` ⇒ `always-on`; sem valor intermediário | INV-5 |
| `x-langfuse-ingestion-version` | String | exatamente `4` | INV-2 |
| corpo do lote | OTLP protobuf (binário) | decodifica para `ExportTraceServiceRequest` com `resourceSpans` com ≥1 span `chat.turn`; sem campo de custo calculado pelo backend | INV-1, INV-4 |

### 7.4 Estratégia de testes

- **Unitários:** configuração do exporter/header/Basic/flag (incluindo default `false` e `always-on`/`always-off`), caracterização da interface congelada e do nome da métrica (testes rápidos, sem rede).
- **Integração (IT):** servidor OTLP fake no `integration-test` capturando método/path/headers/corpo e decodificando o protobuf; cenários exporter disponível, indisponível e lento; flag default-ausente/`false`/`true`; chat com provider fake para provar RF9 sem depender de rede externa.
- **Caracterização:** `git diff --name-only`, `grep` de literal sensível e de nomes proibidos, suíte completa `npm test`. Comando canônico de suíte: `npm test` (`harness.config.json.commands.suiteFull`); backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`.

## 8. Requisitos Funcionais

- **RF-01 — Endpoint e transporte protobuf.** Todo export é `POST /api/public/otel/v1/traces` via OTLP/HTTP, com `Content-Type: application/x-protobuf` e corpo OTLP protobuf decodificável (`ExportTraceServiceRequest`/`resourceSpans`); nenhum path legado, nenhum gRPC, nenhum JSON cru. *(ADR Decisão 1 + Blueprint "Método / path" e "Transporte" + Interface "Path"/"Encoding" + Ajustes de 2026-11-25; RF1 do PRD.)*
- **RF-02 — Autenticação Basic `pk:sk`.** `Authorization: Basic base64(pk:sk)` construído a partir de `LANGFUSE_INGESTION_PK`/`LANGFUSE_INGESTION_SK`, com chave de escopo `Application` (escrita). Nenhum outro esquema é enviado. *(ADR Decisão 2 + Blueprint "Auth" + Interface "Esquema de auth"/variáveis de credencial; RB3 do PRD.)*
- **RF-03 — Header v4 obrigatório.** `x-langfuse-ingestion-version: 4` em toda requisição ao endpoint, sem exceção por lote ou retry. *(ADR Decisão 3 + Blueprint "Header v4" + Interface "Header de versão".)*
- **RF-04 — Exporter isolado e best-effort (RF9).** O exporter é dedicado e alimentado somente pelos dois instrumentos do ADR-001; falha de ingestão nunca altera status, contrato, cota, `turnId`/`clientId` ou headers de resposta do chat, e não dispara retry do chat. *(ADR Decisão 4 + Blueprint "Resposta" + Consequência positiva; RF9/RB2 do PRD.)*
- **RF-05 — Root obrigatório por lote.** Cada lote exportado contém o root `chat.turn` com os atributos de correlação replicados conforme ADR-001; ausência de root é defeito a corrigir no exporter, nunca contornado no chat. *(ADR Decisão 6 + Consequência "sem root").*
- **RF-06 — Sampling por flag com default opt-in.** `HIVE_TELEMETRY_ENABLED` default `false`: ausente ou `false` ⇒ `always-off` (0 span emitido e 0 export); `true` ⇒ `always-on` (100% dos turnos emitidos e exportados). Único botão da primeira fatia; sem taxa intermediária e sem sampler default 10% do Spring Boot. *(ADR Decisão 5 + Interface "Variável de habilitação" + Ajustes de 2026-11-25; PRD §7; §12.)*
- **RF-07 — Falha de export observável e segura.** Cada falha de export incrementa `chat.telemetry.export.failures` (nome lógico Micrometer), exposto como `chat_telemetry_export_failures_total` no scrape Prometheus, e gera log classificado, sem texto bruto, sem `sk-` e sem base64 da credencial. A definição/exposição da série pertence ao ADR-005; ADR-002 garante o incremento. *(ADR Decisão 4 + Interface "Métrica de falha" + Ajustes de 2026-11-25; RF9 do PRD.)*

## 9. Requisitos Não-Funcionais

- **NFR-01 — TLS fora de localhost.** `LANGFUSE_OTLP_TRACES_ENDPOINT` usa `https` em produção; `http` admitido somente para localhost/teste; a URL termina no path congelado e não tem query/fragmento. *(ADR Decisão 1 + Blueprint "Transporte TLS" + Interface "Variável de endpoint".)*
- **NFR-02 — Credencial efêmera.** `pk`/`sk` existem apenas como referência a secret; 0 ocorrência do valor em repo, log, payload OTLP, atributo de span, métrica ou resposta — incluindo `/actuator/env` e `/actuator/configprops` (`show-values=never`). *(ADR Decisão 2 + Interface "Variáveis de credencial"; PRD RB3.)*
- **NFR-03 — Somente traces.** Nenhum OTLP metrics e nenhum OTLP logs são emitidos; nenhum registro global de auto-instrumentação é adicionado. *(ADR Decisão 4 + Interface "Sinais exportados" + Alternativas descartadas; PRD RB5/RB6.)*
- **NFR-04 — Alimentação restrita.** O exporter consome exclusivamente os spans `chat.turn` e `chat.provider.call` do ADR-001; zero span de HTTP/SQL/JVM/infraestrutura no payload. *(ADR Decisão 4; PRD RB6.)*
- **NFR-05 — Encoding protobuf compatível.** Encoding OTLP/HTTP **protobuf** (`Content-Type: application/x-protobuf`), emitido pelo `OtlpHttpSpanExporter` do SDK Java. O canário de redação do ADR-003 não inspeciona JSON cru: decodifica o OTLP ou valida pela Observations API v2 (custo de teste maior, registrado como consequência negativa no ADR). *(ADR Decisão 1 + Interface "Encoding" + Ajustes de 2026-11-25 + Consequências; issue #5598, 2023-07-06, consultado 2026-11-25 — §12.)*
- **NFR-06 — Assíncrono, limitado e sem retry do chat.** Envio em batch com timeout limitado; atraso ou indisponibilidade do destino não propagam exceção ao fluxo de resposta nem aumentam a contagem de chamadas ao provider. *(ADR Decisão 4 + Consequência negativa "janela assíncrona".)*
- **NFR-07 — Interface congelada imutável.** Path, header v4, esquema/escopo de auth, encoding `application/x-protobuf`, nomes `HIVE_TELEMETRY_ENABLED` (default `false`), `LANGFUSE_INGESTION_PK`, `LANGFUSE_INGESTION_SK`, `LANGFUSE_OTLP_TRACES_ENDPOINT` e o nome canônico `chat_telemetry_export_failures_total` (lógico `chat.telemetry.export.failures`) não sofrem rename, remoção ou mudança semântica neste ADR nem nos downstream. *(ADR Interface congelada + Ajustes de 2026-11-25.)*
- **NFR-08 — Rastreabilidade e fontes.** Todo `RF`/`NFR` desta SPEC vira exatamente 1 task em `TASKS.md`; toda afirmação sobre API de terceiro cita URL + data da consulta. *(ADR Fontes; RESEARCH §10.)*
- **NFR-09 — Não regressão.** Contrato público do chat, cota, limites `m/c/t`, `turnId`/`clientId` e headers de rate limit intactos com exporter disponível e indisponível; suíte completa sem nova falha. *(ADR Consequência "best-effort"; PRD RB2, §9.)*

## 10. Critérios de Aceite (executáveis)

Servidor OTLP fake no IT captura método, path, headers, corpo bruto e corpo decodificado de toda requisição. Comando canônico: `npm test`; backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`. Como tracing de export não é auto-configurado em `@SpringBootTest` por padrão (§12), o IT configura o exporter explicitamente.

| Req | Comando / verificação | Asserção observável |
|---|---|---|
| RF-01 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=LangfuseOtlpExportIT` | toda requisição capturada: `method=POST`, path=`/api/public/otel/v1/traces`, `Content-Type: application/x-protobuf`, corpo decodifica para `ExportTraceServiceRequest` com `resourceSpans`; 0 requisição a path legado, 0 gRPC |
| RF-02 | mesmo IT | `base64decode(Authorization após "Basic ")` == `${LANGFUSE_INGESTION_PK}:${LANGFUSE_INGESTION_SK}`; 0 requisição com esquema diferente de `Basic`; escopo `Application` registrado como configuração da chave (lado do Langfuse) |
| RF-03 | mesmo IT | 100% das requisições capturadas com `x-langfuse-ingestion-version: 4`; 0 requisição sem o header |
| RF-04 | IT `LangfuseOtlpBestEffortIT` com exporter recusando conexão | resposta de chat 200, corpo/headers/cota/`turnId`/`clientId` idênticos ao baseline; contagem de chamadas ao provider inalterada; 0 exceção de export na thread do chat |
| RF-05 | decode de cada lote no IT | todo lote com ≥1 span `name=chat.turn`, `isRootObservation=true` e as chaves `langfuse.*` do ADR-001 presentes quando o turno as trouxer; 0 lote sem root |
| RF-06 | IT paramétrico com `HIVE_TELEMETRY_ENABLED` default-ausente, `false` e `true` | default-ausente e `false`: 0 span emitido na instrumentação e 0 requisição capturada; `true`: N turnos ⇒ N lotes com root; 0 config do sampler 10% do Spring Boot |
| RF-07 | IT com falha de export + `curl /actuator/prometheus` | série Prometheus `chat_telemetry_export_failures_total` (nome canônico congelado em ADR-005) incrementada em 1 por lote falho; contador lógico `chat.telemetry.export.failures` no Micrometer; log sem texto bruto, sem `sk-`, sem base64 da credencial |
| NFR-01 | teste de config do perfil prod + IT | endpoint com `https` fora de localhost; URL termina no path congelado; 0 query/fragmento; `http` apenas em localhost/teste |
| NFR-02 | `grep -rn` no repo + captura do IT + `curl /actuator/env` | 0 literal de `sk` no repo; 0 valor da credencial em corpo/log/atributo/resposta; endpoints Actuator sem valor exposto |
| NFR-03 | varredura de todas as requisições do IT + inspeção de beans | 0 requisição a `/v1/metrics` ou `/v1/logs`; 0 auto-instrumentação global registrada; payload só com `resourceSpans` |
| NFR-04 | varredura de nomes de span no payload decodificado | nomes ⊆ `{chat.turn, chat.provider.call}`; 0 span de HTTP/SQL/JVM |
| NFR-05 | asserção de `Content-Type` + decode no IT | `application/x-protobuf`; corpo decodifica para `resourceSpans`; `0` ocorrência de `application/json` no `Content-Type` capturado; 0 config `otel.exporter.otlp.protocol=http/json` |
| NFR-06 | IT com servidor OTLP artificialmente lento + caracterização de latência do chat | chat responde dentro do orçamento caracterizado; contagem de provider calls inalterada; 0 exceção de export propagada |
| NFR-07 | teste de caracterização de strings | conjunto exato de path/header/esquema/encoding/nomes de env/nome canônico da métrica; 0 rename ou remoção |
| NFR-08 | `grep -c '^### OTL-' TASKS.md` + auditoria de URLs | 16 seções ↔ 16 RF/NFR da SPEC; 0 órfão; toda URL de terceiro com data |
| NFR-09 | `npm test` + `mvn -f backend/pom.xml -Pintegration verify` + `git diff --name-only` | 0 falhas; 0 diff em contrato público, cota, limites, RBAC, autenticação; resposta idêntica com exporter up/down |

## 11. Riscos

| Risco | Mitigação | Origem no ADR / fonte |
|---|---|---|
| 🟠 **Canário de redação (ADR-003/`RED-11`) não lê JSON cru.** Com protobuf, o canário precisa decodificar o OTLP ou validar pela Observations API v2 — custo de teste maior que o canário JSON original. Fora desta SPEC: ajuste no ciclo do ADR-003. | OTL-12 decodifica o payload no IT; insumo ao orquestrador para o dono do ADR-003 (não alterado aqui) | ADR Consequência negativa; Ajustes de 2026-11-25; issue #5598 (2023-07-06, consultado 2026-11-25) — §12 |
| 🟡 **Sampler default do Spring Boot (10%, `parent-based-trace-id-ratio`) entregaria menos de 100%.** | RF-06/NFR-07 mapeiam a flag explicitamente para `always-on`/`always-off` e neles se asserce 0 uso do sampler default | ADR Decisão 5; Spring Boot tracing (doc oficial, 2026-11-25) — §12 |
| Credencial exposta por configuração descuidada do Actuator (`env`/`configprops`) | `show-values=never` verificado + NFR-02 | ADR Decisão 2 / RB3 |
| Endpoint `http` em produção ou URL sem o path congelado | NFR-01 + caracterização NFR-07 | ADR Decisão 1 |
| Lote sem root ⇒ Langfuse não cria o trace | RF-05 (asserção por lote), nunca contornado no chat | ADR Decisão 6 |
| Timeout/atraso do exporter vaza para latência do chat | RF-04/NFR-06 com servidor lento | ADR Decisão 4 |
| Auto-instrumentação acrescenta spans e infla custo/viola RB6 | NFR-03/NFR-04 no payload | ADR Alternativas descartadas |
| Rotação manual de `sk` esquecida | registrada como consequência negativa; fora do código deste ADR | ADR Consequências |

**Decisões do architect de 2026-11-25 já incorporadas (não são mais risco em aberto):** encoding protobuf (issue #5598); nome canônico `chat_telemetry_export_failures_total` (Prometheus naming); default `false` da flag com `always-on`/`always-off` (Spring Boot tracing). Nenhuma delas reabre o ADR.

**Fora de escopo desta SPEC (decisões de outros ADRs):** modelo/atributos de spans e correlação (ADR-001); redação e `langfuse.observation.input/output`, incluindo o canário `RED-11` (ADR-003); proxy/RBAC/retention (ADR-004); definição/exposição de métricas de negócio e custo derivado (ADR-005).

## 12. Fontes

Consultadas em 2026-11-25 (salvo indicação). Nenhuma fonte nova de produto foi inventada; as de terceiro abaixo dão suporte às propriedades/configurações citadas nesta SPEC — as decisões permanecem as do `ADR.md`.

- ADR-002 — `ADR.md` deste diretório (Decisão 1–6, Blueprint, Interface congelada, "Ajustes de 2026-11-25", Alternativas, Consequências, Fontes) — 2026-11-25.
- PRD aprovado — `.spec/prd/telemetria-ia-langfuse/PRD.md` §5 (RF1/RF9), §6 (RB2/RB3/RB5/RB6), §7, §16 — 2026-11-25.
- RESEARCH — eixo D1, §7 e §10 (S1/S2/S10/S16/S17) — 2026-11-25.
- Langfuse — OpenTelemetry integration (endpoint, Basic `pk:sk`, header v4, JSON/protobuf, sem gRPC, propagação de atributos) — https://langfuse.com/integrations/native/opentelemetry — 2026-11-25.
- Langfuse — FAQ OTEL (somente traces) — https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25.
- Langfuse — migração v4 — https://langfuse.com/integrations/native/opentelemetry/migration-to-v4 — 2026-11-24.
- Langfuse — compatibilidade/header v4 — https://langfuse.com/docs/compatibility — 2026-11-24.
- Spring Boot 4.1 — Tracing: `management.opentelemetry.tracing.export.otlp.*`, `spring-boot-starter-opentelemetry`, `OtlpHttpSpanExporterBuilderCustomizer`, samplers `always-on`/`always-off` e default `parent-based-trace-id-ratio` a 10%, e "tracing components which are reporting data are not auto-configured with `@SpringBootTest`" — https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html — 2026-11-25.
- Spring Boot 4.1 — Common Application Properties: `management.opentelemetry.tracing.export.otlp.endpoint`, `...otlp.headers.*`, `...otlp.transport` (default `http`), `...otlp.timeout` (default `10s`), `management.endpoint.env.show-values` (default `never`) — https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html — 2026-11-25.
- Spring Boot 4.1.3 — API `OtlpHttpSpanExporterBuilderCustomizer.customize(OtlpHttpSpanExporterBuilder)` — https://docs.spring.io/spring-boot/4.1.3/api/java/org/springframework/boot/micrometer/tracing/opentelemetry/autoconfigure/otlp/OtlpHttpSpanExporterBuilderCustomizer.html — 2026-11-25.
- OpenTelemetry Java — `OtlpConfigUtil`: protocolos `grpc`/`http/protobuf`; cabeçalhos via config — https://github.com/open-telemetry/opentelemetry-java/blob/main/exporters/otlp/all/src/main/java/io/opentelemetry/exporter/otlp/internal/OtlpConfigUtil.java — 2026-11-25.
- OpenTelemetry — especificação de protocolos (`http/json` é opcional para SDKs) — https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/ — 2026-11-25.
- OpenTelemetry Java — issue #5598: "The Java SDK does not have the JSON exporter implemented ... we do not plan on implementing it"; `Content-Type` permanece `application/x-protobuf` — https://github.com/open-telemetry/opentelemetry-java/issues/5598 — publicado 2023-07-06, consultado 2026-11-25.
- Prometheus — Metric and label naming (prefixo, caracteres válidos, sufixo `total` para contadores) — https://prometheus.io/docs/practices/naming/ — 2026-11-25.
- Fatos locais (leitura em 2026-11-25): `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTraceRecorder.java:7`; `backend/dependencies/pom.xml:25,37,114`; `backend/bootstrap/src/main/java/br/com/systec/hive/config/`; `backend/bootstrap/src/main/resources/application.properties`, `application-prod.properties`, `application-dev.properties`; `backend/integration-test/src/test/java/br/com/systec/hive/integrationtest/`; `harness.config.json:13-16`.

**Delta Specs:** nenhuma. ADR-002 não altera doc vivo (`.agents/rules/**`, `AGENTS.md` — PRD RB7). Arquivos de configuração da aplicação são código/config do ADR, não doc vivo.

> **Insumo ao orquestrador (sem decisão tomada aqui):** nenhum bloqueio em aberto neste ADR — as três decisões do `architect` de 2026-11-25 (protobuf, nome canônico da métrica, default `false` da flag) estão aplicadas no ADR e nesta SPEC. Único impacto downstream fora de escopo: `RED-11` (ADR-003) precisa decodificar o protobuf ou validar via Observations API v2; encaminhar ao dono do ADR-003 sem alterar os arquivos daquele ADR neste ciclo.
