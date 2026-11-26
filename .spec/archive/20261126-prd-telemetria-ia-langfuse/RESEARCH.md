---
prd_id: telemetria-ia-langfuse
prd_version: 2
type: research
title: "Insumo de decisão — telemetria de IA com Langfuse self-host v4"
status: insumo-para-adr
data: 2026-11-25
autor: engineer (md-only; não decide arquitetura final)
esteira: "PRD §16 APROVADO em 2026-11-25 → RESEARCH → ADR-INDEX → ADRs → implementação"
---

# RESEARCH — telemetria-ia-langfuse

> **Natureza:** insumo com fonte. Não é ADR, não fixa arquitetura, não cria branch/código/dependency.
> Cada afirmação sobre API/padrão de terceiro traz URL + data da consulta (2026-11-24/25).
> Fatos do repositório são citados por `caminho:linha` (leitura em 2026-11-25).
> Onde não há fonte, a afirmação está rotulada **inferência** e acompanha proposta de verificação.
> Recomendação é única por eixo; o veredito final pertence ao `architect`.

## 0. Metodologia e critérios

Critérios aplicados a toda alternativa: **custo** (licença + componentes novos), **acoplamento** (número de portas/SDKs/formatos proprietários), **testabilidade** (o RF correspondente é mensurável com IT/canário), **segurança** (vazamento de texto/credencial, fail-closed), **aderência ao stack do destino** (Spring Boot 4.1.0 + LangChain4j 1.19.0 + Actuator — `backend/dependencies/pom.xml:25,37,114`).

Hierarquia de fonte usada: documentação oficial do Langfuse e da Spring, código no tag `1.19.0` do LangChain4j (não snapshot de apidocs), specs abertas onde aplicável. Toda URL abaixo foi consultada em 2026-11-25 salvo indicação em contrário.

## 1. Eixo D1 — Transporte/ingestão (dono: ADR-002)

**Pergunta:** como o backend entrega spans ao Langfuse self-host v4 sem virar novo vetor de vazamento?

| Alternativa | Fonte (URL + data) | Trade-off | Recomendação |
|---|---|---|---|
| **A — OTLP/HTTP direto** para `/api/public/otel/v1/traces`, Basic `pk:sk`, header `x-langfuse-ingestion-version: 4`, exporter isolado e best-effort | https://langfuse.com/integrations/native/opentelemetry — 2026-11-25; https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25 | Ganha: formato padrão, sem SDK proprietário Java, real-time no modelo v4 e nas APIs v2 com o header; Java é explicitamente suportado por OTLP/HTTP. Perde: gRPC não existe; exige montar os atributos `langfuse.*` na mão e propagar atributos de trace para todos os spans | **Aceita** |
| **B — OpenTelemetry Collector** no meio (batch, `memory_limiter`, filter/transform) | https://langfuse.com/integrations/native/opentelemetry — 2026-11-25; https://langfuse.com/docs/observability/features/masking — 2026-11-25 | Ganha: filtro e política central, desacoplamento do exporter. Perde: mais um componente operacional; masking no Collector acontece **depois** de o dado sair da aplicação; filtrar spans pode deixar trace incompleto — o Langfuse exige o root span para criar o trace | **Descartada na 1ª fatia** — candidata a defesa em profundidade posterior |
| **C — SDK Langfuse (Python/JS)** | https://langfuse.com/integrations/native/opentelemetry — 2026-11-25; https://langfuse.com/docs/observability/features/masking — 2026-11-25 | Ganha: helpers de modelo/usage/máscara prontos. Perde: a doc recomenda SDK só para Python/JS; o destino é Java/Spring Boot 4.1.0 (`backend/dependencies/pom.xml:25`) | **Descartada** — incompatível com o stack |
| **D — endpoint legado** `/api/public/ingestion` | https://langfuse.com/integrations/native/opentelemetry — 2026-11-25; https://langfuse.com/self-hosting/security/data-masking — 2026-11-25 | Ganha: nada novo. Perde: OTLP substitui o endpoint deprecado; o callback de máscara EE não cobre o legado | **Descartada** |

**Recomendação final D1:** alternativa **A**. OTLP/HTTP com header v4, Basic `pk:sk` em cofre, exporter isolado que nunca altera a resposta do chat (RF9). Collector fica fora da primeira fatia; se entrar, masking continua na origem (D3), não só nele. Atributos de correlação devem ser replicados em todos os spans porque o Langfuse filtra/agrega por observation, não só por trace (fonte S1).

## 2. Eixo D2 — Instrumentação e modelo de spans (dono: ADR-001)

**Pergunta:** onde nasce o span raiz do turno e o span filho da chamada ao provider, incluindo retries?

| Alternativa | Fonte (URL + data) | Trade-off | Recomendação |
|---|---|---|---|
| **A — 1 root por turno na porta existente + 1 filho por chamada via `ChatModelListener`** | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/listener/ChatModelListener.java — 2026-11-25 (interface: `onRequest`/`onResponse`/`onError` default); repo: `assistant/api/.../diagnostics/ChatTraceRecorder.java:7`, `assistant/impl/.../AssistantTurnGateway.java:117,131` | Ganha: usa a porta já existente, distingue tentativas (retry policy em `:117`) e preserva correlação. Perde: exige propagar o contexto do listener para o span do turno — mecânica exata é **inferência**, a validar com IT de 2 tentativas (RF2) | **Aceita** |
| **B — `ObservationChatModelListener` (Micrometer Observation)** | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25 | Ganha: implementação pronta da interface, duração/contexto via `ObservationRegistry` e `DistributionSummary gen_ai.client.token.usage` com tags de provider/modelo. Perde: marcada `@Experimental`; por si só não entrega a hierarquia turno→provider no Langfuse nem o `langfuse.observation.type=generation` | **Aceita como insumo de D5, não como contrato de spans** |
| **C — só `MicrometerMetricsChatModelListener`** | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-micrometer-metrics/src/main/java/dev/langchain4j/micrometer/metrics/listeners/MicrometerMetricsChatModelListener.java — 2026-11-25 | Ganha: métrica de token pronta seguindo semconv. Perde: `onError` é no-op e não há trace/span | **Descartada como instrumentação de trace**; útil só para métrica |

**Nomenclatura (ressalva do PRD §16 resolvida com fonte):** `ChatModelListener` é a **interface** (`langchain4j-core/.../model/chat/listener/ChatModelListener.java`); `ObservationChatModelListener` é uma **implementação** (`langchain4j-observation/.../listener/ObservationChatModelListener.java`, `@Experimental`). O ADR deve nomear a interface no contrato e a implementação apenas se a adotar. A apidocs citada no PRD servia o snapshot `1.25.0-beta35-SNAPSHOT` (PRD §16 — 2026-11-24); as assinaturas acima foram verificadas no **tag `1.19.0`** (URLs acima — 2026-11-25). Usar o tag como SSoT de assinatura.

**Recomendação final D2:** alternativa **A** para o contrato de spans; **B** como fonte opcional de métricas em D5, sujeita a decisão do ADR. Root = turno; filho = tentativa ao provider; `provider`/`model`/usage/falha no filho (RF1/RF2). Sem spans de HTTP/SQL/infra.

## 3. Eixo D3 — Redação na origem e privacidade (dono: ADR-003)

**Pergunta:** como texto de prompt/resposta entra em produção sem carregar PII ou segredo — e o que acontece se a redação falhar?

| Alternativa | Fonte (URL + data) | Trade-off | Recomendação |
|---|---|---|---|
| **A — redação no backend antes do OTLP export, fail-closed** | https://langfuse.com/docs/observability/features/masking — 2026-11-25 (" escolha esta abordagem quando o dado sensível não deve sair da aplicação"); https://langfuse.com/self-hosting/security/data-masking — 2026-11-25 | Ganha: literal sensível nunca deixa o backend (RB1/RF3); falha do redator descarta texto e mantém metadados (RF4). Perde: regras de CPF/CNPJ/e-mail/telefone/tokens/senhas/chaves vivem no código e precisam de canário | **Aceita — obrigatória** |
| **B — masking server-side EE no ingest, `LANGFUSE_INGESTION_MASKING_CALLBACK_FAIL_CLOSED=true`** | https://langfuse.com/self-hosting/security/data-masking — 2026-11-25 | Ganha: política central. Perde: **exige licença Enterprise**; default é `false` (fail-open); evento fica em blob storage antes da máscara; cobre só `/api/public/otel` | **Descartada na 1ª fatia** (Community congelado) — útil como rede de segurança futura |
| **C — masking no Collector** | https://langfuse.com/docs/observability/features/masking — 2026-11-25 | Ganha: consistente entre serviços sem EE. Perde: dado já saiu da aplicação; conflita com RF3 ("antes de sair do backend") | **Descartada como primária**; admissível só em camada extra |
| **D — hook client-side do SDK Langfuse** (`mask_otel_spans`, `LangfuseSpanProcessor({mask})`) | https://langfuse.com/docs/observability/features/masking — 2026-11-25 | Ganha: fail-closed nativo (exceção derruba o batch). Perde: hooks documentados para Python/JS; stack é Java | **Descartada** — incompatível com o stack |

**Recomendação final D3:** alternativa **A**, fail-closed por construção: se o redator não aplicar a política, o span segue sem `langfuse.observation.input`/`output` e com `provider`/`model`/usage intactos. B/C somente como defesa em profundidade se licença EE ou Collector forem deliberadamente adotados — nunca como substituto da redação na origem. Observação de fonte: o mecanismo EE tem *fail-open* como default (variável `...FAIL_CLOSED=false`), reforçando que ele não satisfaz RF4 sozinho (URL acima — 2026-11-25).

## 4. Eixo D4 — Acesso e retenção no Community (dono: ADR-004)

**Pergunta:** Admin Hive + Suporte leem, time de IA administra, 30 dias — sem licença EE.

| Alternativa | Fonte (URL + data) | Trade-off | Recomendação |
|---|---|---|---|
| **A — auth no reverse proxy + escopo de API key + job externo de retenção** | https://langfuse.com/docs/administration/rbac — 2026-11-25 (permissões de API key disponíveis no self-host; `Application` = enviar traces, `Viewer` = só leitura); https://langfuse.com/self-hosting/security/authentication-and-sso — 2026-11-25; https://langfuse.com/self-hosting/configuration/hardening — 2026-11-25 (TLS/VPN/zero-trust na frente, `AUTH_DISABLE_SIGNUP`, `AUTH_DISABLE_USERNAME_PASSWORD`, admin endpoint restrito a proxy interno); https://langfuse.com/docs/administration/data-retention — 2026-11-25 | Ganha: cumpre a decisão do operador sem EE; chave de ingestão com escopo `Application` não serve para leitura (RF5). Perde: proxy **não** é RBAC por projeto dentro do Langfuse; retenção nativa é EE e o default no self-host é indefinido — job externo precisa de dono, schedule e parâmetro auditáveis (RF6) | **Aceita** |
| **B — RBAC por projeto + Data Retention nativos (EE)** | https://langfuse.com/docs/administration/rbac — 2026-11-25; https://langfuse.com/docs/administration/data-retention — 2026-11-25; https://langfuse.com/self-hosting/configuration/hardening — 2026-11-25 | Ganha: papéis por projeto e limpeza nightly (mínimo 3 dias), `LANGFUSE_INIT_PROJECT_RETENTION`. Perde: Self Hosted = **Enterprise Edition** nas duas tabelas de disponibilidade; contraria a decisão congelada "Community sem EE" | **Descartada** — só se operador reabrir licença |
| **C — Langfuse Cloud** | https://langfuse.com/docs/administration/data-retention — 2026-11-25; PRD §8-B — 2026-11-24 | Ganha: retenção gerenciada. Perde: janela de acesso ≠ exclusão; residência/controle de dado contrariam a decisão de self-host | **Descartada** |

**Recomendação final D4:** alternativa **A**. RF5 se mede com 403 no proxy para usuário sem papel e negação de leitura com a credencial de ingestão; RF6 com trace de 31 dias ausente e 29 presente após o job, parâmetro `30` auditável. **Risco explícito ao `architect`:** "RBAC no proxy" cobre autenticação/autorização de acesso à UI/API, mas o RBAC **por projeto** do Langfuse é EE (fonte S7) — o ADR não deve afirmar equivalência. **Pergunta em aberto (HITL):** dono operacional e mecanismo de auditoria do job externo não foram especificados pelo operador; não inventar — ver §7.

## 5. Eixo D5 — Métricas operacionais e custo derivado (dono: ADR-005)

**Pergunta:** onde vivem as métricas e quem calcula o custo?

| Alternativa | Fonte (URL + data) | Trade-off | Recomendação |
|---|---|---|---|
| **A — Micrometer/Prometheus para operação + custo inferido pelo Langfuse** | https://docs.spring.io/spring-boot/reference/actuator/metrics.html — 2026-11-25 (Actuator + Micrometer, registries incl. Prometheus); https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html — 2026-11-25 (`GET /actuator/prometheus`); https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25; https://langfuse.com/docs/metrics/features/metrics-api — 2026-11-25 | Ganha: público de operação separado do de análise; custo sem catálogo no backend, consultável por `GET /api/public/v2/metrics` (`measure=totalCost`, `aggregation=sum` → `sum_totalCost`). Perde: custo inferido exige observation do tipo `generation` (ou `embedding`), usage enviado **e** model definition com preço; no self-host os preços default acompanham a versão instalada e só valem para generations posteriores | **Aceita** |
| **B — enviar OTLP metrics ao Langfuse** | https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links — 2026-11-25 | Ganha: pipeline único. Perde: Langfuse ingere **somente traces**; `/api/public/otel/v1/metrics` aceita a requisição e **descarta** os dados — RF7 seria impossível | **Descartada** |
| **C — backend calcula e envia `cost_details`** | https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25 | Ganha: custo exato quando o provider informa preço. Perde: viola RF8 ("sem o backend enviar valor de custo") e acopla catálogo ao código | **Descartada** |

**Recomendação final D5:** alternativa **A**. O exporter deve emitir spans com `langfuse.observation.type=generation`, `langfuse.observation.model.name`, `langfuse.observation.usage_details` e, quando houver texto redigido, `langfuse.observation.input`/`output` — mapeamentos documentados em https://langfuse.com/integrations/native/opentelemetry (2026-11-25). Atenção: buckets de usage são excludentes (`input` não inclui cache; `gen_ai.usage.*` é normalizado, mas `langfuse.observation.usage_details` é armazenado como veio) — https://langfuse.com/docs/observability/features/token-and-cost-tracking (2026-11-25). Sem model definition, `sum_totalCost` será 0 mesmo com usage correto. O header v4 evita atraso de até 15 min nas APIs v2 — https://langfuse.com/docs/metrics/features/metrics-api (2026-11-25).

## 6. Ressalvas do PRD §16 — endereçadas com fonte

| Ressalva | Endereçamento | Fonte (URL + data) |
|---|---|---|
| RBAC **por projeto** e Data Retention são EE no self-host | Confirmado nas tabelas de disponibilidade; decisão Community mantida com proxy + job externo; ADR não deve chamar isso de "RBAC nativo" | https://langfuse.com/docs/administration/rbac — 2026-11-25; https://langfuse.com/docs/administration/data-retention — 2026-11-25; https://langfuse.com/self-hosting/configuration/hardening — 2026-11-25 |
| Custo inferido exige observation `generation` + model definition | Confirmado; traduzido em atributos OTLP obrigatórios no eixo D5 | https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-25; https://langfuse.com/integrations/native/opentelemetry — 2026-11-25 |
| apidocs servia snapshot `1.25.0-beta35-SNAPSHOT`; usar tag `1.19.0` | Assinaturas verificadas no tag; apidocs não é SSoT de assinatura | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/listener/ChatModelListener.java — 2026-11-25; PRD §16 — 2026-11-24 |
| `ChatModelListener` (interface) × `ObservationChatModelListener` (impl) | Confirmado nos dois arquivos do tag; `ObservationChatModelListener implements ChatModelListener` e é `@Experimental` | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25 |

## 7. Riscos, premissas não validadas e perguntas em aberto

**Riscos (com fonte ou rótulo):**
- Execução do exporter Langfuse não pode bloquear resposta de chat (RF9); construção de exporter isolado é **inferência** de projeto — validar com IT de exporter indisponível.
- `ObservationChatModelListener` é `@Experimental` no tag 1.19.0 (fonte S14 — 2026-11-25): se o ADR o adotar, registrar risco de mudança de API em upgrade.
- Preços default do self-host são os da versão instalada; upgrade é necessário para receber preços oficiais novos (fonte S9 — 2026-11-25). Catálogo é ponto de decisão do operador, não inventado aqui.
- Sem header v4, dados de OTLP direto podem levar até 15 min para aparecer nas APIs v2 (fonte S10 — 2026-11-25).
- Estado atual do `runPrdChecks('.')` (2026-11-25): `PRD-01` FAIL apenas pelo órfão pré-existente `chat-model-override/PROPOSAL.md`; `ADR-01`/`ADR-02` PASS no baseline. Ver §8 para o efeito mecânico do ADR-INDEX declarativo.

**Premissas não validadas (não são fatos):**
- "RBAC no proxy equivale a RBAC por projeto no Langfuse" — **falso** segundo fonte S7; não usar essa equivalência.
- Mecânica exata de propagação de contexto entre `ChatModelListener` e o span raiz do turno — **inferência**; exige IT de 2 tentativas (RF2) no ADR-001.
- Responsável, agendamento e prova de auditoria do job de retenção — **pergunta em aberto ao operador** (o operador respondeu "job externo auditável", mas não nomeou dono nem schedule). Encaminhar ao orquestrador para HITL; não preencher com chute.
- Definição de preço do modelo no self-host para RF8 — **pergunta em aberto ao operador/architect**: usar preços default da versão ou definition custom (`POST /api/public/models`)? A fonte S9 documenta ambas; a escolha é do ADR/operador.

**Pontos que exigem decisão do `product-manager`/`architect`:**
1. ADR-001: adotar apenas spans manuais (D2-A) ou também `ObservationChatModelListener` (D2-B) como ponte de métricas?
2. ADR-002: OTLP direto (D1-A) é definitivo? Collector fica fora mesmo como camada de masking (D1-B)?
3. ADR-003: apenas redação na origem (D3-A) ou acrescentar callback EE como rede de segurança mediante licença?
4. ADR-004: como o proxy representará Admin Hive/Suporte/time de IA sem RBAC por projeto nativo; quem opera o job de 30 dias?
5. ADR-005: quais model definitions/preços alimentarão o custo inferido no self-host.

## 8. Nota operacional sobre o ADR-INDEX declarativo

`ADR-INDEX.md` foi escrito com **5 linhas declaradas** (1:1 com o PRD §13), `status: todo`, `unit: adr`, sem `ADRs/**`, branch, push ou merge — conforme a restrição do insumo. O checador `.opencode/plugins/lib/prd-adr-pipeline/checks.mjs:353-356` exige que todo `dir` da tabela exista no disco; portanto, **enquanto os diretórios de ADR não existirem, `ADR-01` reportará 5 linhas sem dir correspondente**. `ADR-02` permanece conforme (ordem 1..5, branches no regex, `status: todo`, `merge` vazio). Esta é uma tensão entre "declarar o ledger agora" e "ledger 1:1 com o disco" — encaminhada ao orquestrador, **não** resolvida criando diretórios proibidos nem burlando o formato da tabela. A linha do tempo correta é: `architect` cria `ADRs/ADR-00N-{slug}/` junto com cada ADR; o índice passa a ser 1:1 com o disco nesse momento.

## 9. Mapa eixo → ADR → RF

| Eixo | ADR | RFs atendidos |
|---|---|---|
| D1 transporte | ADR-002 | RF1, RF9 |
| D2 instrumentação | ADR-001 | RF1, RF2 |
| D3 redação | ADR-003 | RF3, RF4 |
| D4 acesso/retention | ADR-004 | RF5, RF6 |
| D5 métricas/custo | ADR-005 | RF7, RF8 |

## 10. Fontes consultadas

| # | URL | Data | Uso principal |
|---|---|---|---|
| S1 | https://langfuse.com/integrations/native/opentelemetry | 2026-11-25 | endpoint OTLP/HTTP, Basic `pk:sk`, header v4, atributos `langfuse.*`, root span, propagação |
| S2 | https://langfuse.com/faq/all/opentelemetry-logs-metrics-span-links | 2026-11-25 | só traces; endpoint de metrics aceita e descarta |
| S3 | https://langfuse.com/docs/observability/features/masking | 2026-11-25 | masking na aplicação vs Collector vs SDK; fail-closed do SDK |
| S4 | https://langfuse.com/self-hosting/security/data-masking | 2026-11-25 | masking server-side EE; `...FAIL_CLOSED` default `false`; só `/api/public/otel` |
| S5 | https://langfuse.com/docs/administration/rbac | 2026-11-25 | papéis por projeto = EE; escopo de API key (`Application`/`Viewer`) disponível no self-host |
| S6 | https://langfuse.com/docs/administration/data-retention | 2026-11-25 | retenção nativa = EE; default indefinido; mínimo 3 dias; nightly |
| S7 | https://langfuse.com/self-hosting/security/authentication-and-sso | 2026-11-25 | disable signup, SSO, disable username/password |
| S8 | https://langfuse.com/self-hosting/configuration/hardening | 2026-11-25 | proxy/TLS/VPN para admin; retenção e audit logs = EE |
| S9 | https://langfuse.com/docs/observability/features/token-and-cost-tracking | 2026-11-25 | custo inferido: `generation`, usage, model definition; preços no self-host; buckets excludentes |
| S10 | https://langfuse.com/docs/metrics/features/metrics-api | 2026-11-25 | `GET /api/public/v2/metrics`, `sum_totalCost`, `isRootObservation`, atraso sem header v4 |
| S11 | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/listener/ChatModelListener.java | 2026-11-25 | interface e métodos default |
| S12 | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java | 2026-11-25 | implementação `@Experimental`, Observation + `gen_ai.client.token.usage` |
| S13 | https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-micrometer-metrics/src/main/java/dev/langchain4j/micrometer/metrics/listeners/MicrometerMetricsChatModelListener.java | 2026-11-25 | métricas de token; `onError` no-op |
| S14 | https://docs.spring.io/spring-boot/reference/actuator/metrics.html | 2026-11-25 | Actuator + Micrometer; registry Prometheus |
| S15 | https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html | 2026-11-25 | `GET /actuator/prometheus` |
| S16 | https://langfuse.com/integrations/native/opentelemetry/migration-to-v4 | 2026-11-24 | pesquisa prévia do PRD §15 (migração v4) |
| S17 | https://langfuse.com/docs/compatibility | 2026-11-24 | pesquisa prévia do PRD §15 (compatibilidade/header v4) |
| S18 | https://langfuse.com/self-hosting | 2026-11-24 | pesquisa prévia do PRD §15 (self-host) |
| S19 | https://langfuse.com/docs/api-and-data-platform/features/public-api | 2026-11-24 | co-assinatura do PRD §16 (Observations API v2) |

> Fatos locais (sem URL): `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTraceRecorder.java:7`; `ChatTrace.java:15,45-64`; `backend/assistant/impl/.../AssistantTurnGateway.java:117,131`; `backend/dependencies/pom.xml:25,37,114` — leitura em 2026-11-25. Regras: `.agents/rules/prd-adr-pipeline.md:18-39`, `.opencode/plugins/lib/prd-adr-pipeline/checks.mjs:319-441,443-532`.
