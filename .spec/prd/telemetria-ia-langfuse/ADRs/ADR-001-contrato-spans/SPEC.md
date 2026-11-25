<!-- scope: adr/ADR-001 -->
# SPEC — ADR-001: Contrato de telemetria — modelo de spans turno/provider, atributos de correlação e fronteira com `ChatTraceRecorder`

> **Escopo:** somente a decisão congelada no `ADR.md` deste diretório. Cada `RF`/`NFR` abaixo aponta para a cláusula do ADR que o origina. Não especifica transporte OTLP (ADR-002), redação de texto (ADR-003), acesso/retention (ADR-004) nem métricas/custo (ADR-005).
>
> **Rastreabilidade:** `ADR.md` (decisão + interface congelada) → esta SPEC → `TASKS.md` → evidência no `VERIFY.md` pós-merge. `depende-de: —`. Status do ADR: Proposto (sem merge).

## 1. ADRs

### ADR-001 (este ciclo)

- **Decision:** dois níveis de span, e só dois — raiz `chat.turn` por turno (fechada/enriquecida em `ChatTraceRecorder.record(ChatTrace)`) e filha `chat.provider.call` por tentativa ao provider (captura pela interface `ChatModelListener` 1.19.0, `onRequest`/`onResponse`/`onError`), com `provider`, `model`, usage, duração e falha classificada. Atributos `langfuse.*` de correlação replicados em root e filhos. `ChatTrace` continua sem texto. (ADR Decisão 1–5)
- **Context:** hoje há 1 linha de log por mensagem, sem hierarquia e sem texto; não é possível responder "qual turno, qual tentativa, qual modelo" em uma consulta. `ChatTraceRecorder.record` é a porta de saída existente; o provider é chamado em `AssistantTurnGateway.java:131` (retry em `:117`) e o trace é fechado em `TurnOutcomeMapper.java:188`, depois das tentativas. (ADR Contexto)
- **Trade-offs:** contrato de spans verificável com provider fake e Observations API v2 versus mapeamento manual de atributos e propagação de contexto entre listener e `record` (inferência, provada só no IT de 2 tentativas). Sem SDK novo, sem `ObservationChatModelListener` (`@Experimental` no tag 1.19.0) como contrato. (ADR Decisão 3, Consequências, Alternativas descartadas)

ADRs afetados: nenhum. ADR-002 e ADR-003 dependem desta interface congelada; ADR-004 e ADR-005 consomem os atributos aqui definidos.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `assistant` — turno de chat e sua telemetria; compõe `provider` somente pela porta já existente. Nenhuma dependência nova entre módulos.
- **System Type:** Spring Boot 4.1.0 + LangChain4j 1.19.0. `assistant/api` detém a porta e o record; `assistant/impl` detém a captura e o escopo de turno; `provider/langchain4j` é a origem do callback do listener.
- **Tenant Provisioning:** N/A — nenhum tenant, sessão, cliente ou credencial é criado. O valor de `tenantId` é lido de `ChatTrace.tenantId` quando presente (ADR Interface congelada).
- **Tenant Lifecycle:** N/A — inalterado; ADR-001 não toca ciclo de tenant nem RBAC da aplicação (PRD RB2/RB7).

## 3. Identity & Access Management

- **IdP / Protocol:** inalterados. Autenticação do destino não é modificada nesta fatia (decisão fora do ADR-001).
- **RBAC:** nenhuma permissão nova; `@HasPermission` existentes preservados. Credencial de ingestão `pk:sk` pertence ao ADR-002 e não é lida, logada ou emitida aqui.
- **MFA:** N/A — proibido tocar o ciclo de MFA (PRD §2, fora de escopo).
- **Sessão/cliente:** `sessionId`, `tenantId`, `turnId`, `clientId` e `promptTemplateId` são **lidos** de `ChatTrace`, nunca gerados. `clientId` ausente no fluxo humano ⇒ `langfuse.user.id` é omitido, nunca inventado (ADR Interface congelada).

## 4. Security, Key Management & Disaster Recovery

- **Chaves:** nenhuma chave nova. Nenhuma credencial entra em span, log, payload ou resposta nesta fatia.
- **Texto:** nenhum prompt/resposta em `ChatTrace`, log, atributo de span ou `status_message`. O rótulo de falha reusa `FailureClassifier.causeOf` (conjunto fechado `RootSimpleName:causeCode`), sem stack trace e sem texto bruto, conforme ADR Interface congelada.
- **Exposição:** contrato público do chat, cota, limites `m/c/t`, `turnId`/`clientId` e headers de rate limit intactos (PRD RB2).
- **RTO/RPO:** N/A — nenhum estado persistido novo; retention pertence ao ADR-004.

## 5. Data Model & Concurrency

- Nenhuma entidade, tabela, migration Flyway/Liquibase ou chave Redis é criada ou alterada. `ChatTrace` permanece com seus componentes atuais e sem texto (ADR Decisão 4 + Interface "Porta existente").
- **ERD:** N/A — nenhuma entidade nova neste ADR (ADR-001 não cria tabela; traces vivem no Langfuse — PRD §11).
- **Migrations:** N/A — sem migration Java neste ciclo.
- **Concorrência:** correlação vive em escopo de turno; o ADR registra que a mecânica é thread-local por construção e que virada reativa exige novo ADR (Consequência negativa). Nenhum lock, transação ou fila nova.

## 6. Event Model, Integrations & Messaging Isolation

- **Event Registry:** N/A — nenhum evento de domínio novo. Spans OTLP não são evento de domínio; a exportação é decidida no ADR-002.
- Integração de saída desta fatia é a própria porta `ChatTraceRecorder.record(ChatTrace)`, inalterada.
- **Isolamento:** atributos de correlação são propagados por escopo de turno; nenhum atributo de um turno pode aparecer no span de outro (RF1 do PRD, reforçado pela Decisão 2 do ADR).

## 7. Invariants & Property-Based Testing

### 7.1 Invariantes de span

| ID | Propriedade | Verificação |
|---|---|---|
| INV-1 | Por turno: exatamente 1 span raiz `chat.turn`, `langfuse.observation.type=span`, sem parent (`isRootObservation=true`) | IT de contrato: contagem de roots no payload OTLP / Observations API v2 |
| INV-2 | Por tentativa ao provider: exatamente 1 filho `chat.provider.call`, `type=generation`, `attempt ≥ 1` | IT com provider fake de 2 tentativas: `filhos == tentativas` |
| INV-3 | Profundidade máxima = 2; zero spans de HTTP/SQL/JVM/infra | varredura do payload OTLP por nome de span |
| INV-4 | Todo span (root e filhos) tem `startTimeUnixNano > 0` e `endTimeUnixNano ≥ startTimeUnixNano` | asserção em cada span do payload |
| INV-5 | Correlação replicada em root e filhos; valor presente pertence ao turno; `clientId` nulo ⇒ chave ausente | IT com 2 turnos de tenants distintos + caso humano sem `clientId` |
| INV-6 | Filho falho ⇒ `level=ERROR` + `status_message` classificado; demais `DEFAULT`; sem stack trace e sem texto bruto | asserção no payload + `FailureClassifierTest` |
| INV-7 | `ChatTraceRecorder.record(ChatTrace)` e `ChatTrace` inalterados; zero chaves de texto nesta fatia | teste de assinatura/reflection + `git diff --name-only` |

### 7.2 Invariantes de workflow

- **Turno:** entrada do turno → captura das tentativas pelo listener → `record(ChatTrace)` fecha/enriquece o root. Root nunca é aberto por tentativa (ADR Decisão 2).
- **Retry:** 2 tentativas = 1 root + 2 filhos (`attempt=1`, `attempt=2`). Retry não gera root novo nem trace órfão (ADR "Inferência a validar").
- **Falha:** `level=ERROR` pertence somente ao filho da tentativa que falhou; o root permanece com `DEFAULT` (ADR Interface congelada).

### 7.3 Restrições de geração de dados de teste

| Campo | Tipo | Restrição | Objetivo |
|---|---|---|---|
| `sessionId` | UUID | não nulo nos cenários de correlação | replicação exata em root/filhos |
| `tenantId`, `turnId`, `promptTemplateId` | UUID | nulo ou UUID | presença condicional da chave |
| `clientId` | String | nulo ou não vazio | omissão no fluxo humano |
| `attempt` | int | ≥ 1 | contagem de retries |
| `startTimeUnixNano`, `endTimeUnixNano` | long | `end ≥ start > 0` | timestamps OTLP |
| `failure` | String | rótulo classificado, sem `at ...` e sem texto do prompt | nenhum texto bruto |

## 8. Requisitos Funcionais

- **RF-01 — Root por turno.** Exatamente 1 span raiz `chat.turn` por turno, `langfuse.observation.type=span`, sem parent (`isRootObservation=true`), `langfuse.trace.name=chat.turn`. *(ADR Decisão 1/2 + Interface "Nome do root" e "langfuse.trace.name"; RF1 do PRD.)*
- **RF-02 — Filho por tentativa.** Exatamente 1 span filho `chat.provider.call` por chamada ao provider, inclusive cada retry, `type=generation`, com `provider`, `model`, usage de input/output/total (quando reportado) e duração. *(ADR Decisão 1/3 + Interface "Nome do filho"; RF2 do PRD.)*
- **RF-03 — Correlação replicada.** `sessionId → langfuse.session.id`, `clientId → langfuse.user.id`, `tenantId → langfuse.trace.metadata.tenant_id`, `turnId → ...turn_id`, `promptTemplateId → ...prompt_template_id`, replicados em root e filhos quando presentes; `clientId` ausente no fluxo humano ⇒ chave omitida. *(ADR Decisão 2 + Interface, 5 linhas de correlação.)*
- **RF-04 — Falha classificada no filho.** Filho falho com `langfuse.observation.level=ERROR` e `status_message` igual ao rótulo classificado de falha; demais com `DEFAULT`; sem stack trace e sem texto bruto. *(ADR Decisão 3 + Interface "level/status_message".)*
- **RF-05 — Attempt por tentativa.** `langfuse.observation.metadata.attempt` = inteiro ≥ 1, somente no filho, contando as tentativas da retry policy. *(ADR Decisão 3 + Interface "attempt".)*
- **RF-06 — Root fechado na porta existente.** Root aberto na entrada do turno e fechado/enriquecido em `ChatTraceRecorder.record(ChatTrace)`, sem alterar assinatura da porta nem campos do `ChatTrace`. *(ADR Decisão 2 + Interface "Porta existente".)*
- **RF-07 — Texto fora do `ChatTrace`.** Nenhum prompt/resposta entra em `ChatTrace`, log ou atributo de span nesta fatia; portador de texto e `langfuse.observation.input/output` pertencem ao ADR-003. *(ADR Decisão 4 + Interface "Porta existente".)*
- **RF-08 — Propagação listener→root provada por IT.** IT com provider fake que falha na 1ª tentativa e responde na 2ª: 2 filhos (1º com `level=ERROR`), 1 root, 0 órfãos, mesmo `traceId` e correlação do turno em todos os spans. *(ADR "Inferência a validar" + Consequência negativa.)*

## 9. Requisitos Não-Funcionais

- **NFR-01 — Profundidade fixa.** Exatamente 2 níveis; nenhum span de HTTP, SQL, JVM ou infraestrutura. *(ADR Decisão 1 + Interface "Profundidade"; PRD RB6.)*
- **NFR-02 — Timestamps OTLP.** `startTimeUnixNano` e `endTimeUnixNano` preenchidos em root e filhos — Langfuse reescreve span sem ambos. *(ADR Decisão 5 + Interface "Timestamps OTLP".)*
- **NFR-03 — Nomenclatura congelada.** Nomes `chat.turn`/`chat.provider.call`, `langfuse.trace.name=chat.turn` e as chaves `langfuse.*` da tabela são imutáveis (sem rename, remoção ou mudança semântica) neste e nos ADRs downstream. *(ADR Interface congelada.)*
- **NFR-04 — Listener é a interface.** Instrumentação usa `dev.langchain4j.model.chat.listener.ChatModelListener` do `langchain4j-core` 1.19.0; `ObservationChatModelListener` (`@Experimental`) não é contrato de spans nem fonte de métrica nesta fatia. *(ADR Decisão 3 + Alternativas descartadas; fontes S11/S12 do RESEARCH.)*
- **NFR-05 — Rastreabilidade e fontes.** Todo `RF`/`NFR` desta SPEC vira 1 task em `TASKS.md`; toda afirmação sobre API de terceiro cita URL + data da consulta. *(ADR Fontes; RESEARCH §10.)*
- **NFR-06 — Não regressão.** Contrato público do chat, cota, limites e autenticação inalterados; suíte completa sem nova falha. *(ADR Consequência "Reusa a porta existente"; PRD RB2.)*
- **NFR-07 — Isolamento de escopo de turno.** Correlação é thread-local por turno; nenhum atributo de outro turno/tenant aparece em qualquer span. *(ADR Decisão 2 + Consequência negativa.)*

## 10. Critérios de Aceite (executáveis)

Comando canônico de suíte: `npm test` (`harness.config.json.commands.suiteFull`). Comandos de backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`. O payload OTLP é capturado pelo fixture de teste; a contagem de observations usa a Observations API v2 conforme ADR/PRD RF1.

| Req | Comando / verificação | Asserção observável |
|---|---|---|
| RF-01 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=ChatSpanContractIT` | 1 turno ⇒ exatamente 1 span `name=chat.turn`, `langfuse.observation.type=span`, `parentSpanId` ausente, `langfuse.trace.name=chat.turn`; 2 turnos ⇒ 2 `traceId` distintos |
| RF-02 | mesmo IT, provider fake com retry | `count(chat.provider.call) == count(tentativas) == 2`; cada filho com `provider`, `model` e `usage.input/output/total` quando reportados |
| RF-03 | mesmo IT, `ChatTrace` com 5 IDs não nulos | cada chave `langfuse.*` de correlação presente em root **e** filhos com o valor exato do turno; com `clientId=null`, `langfuse.user.id` ausente; com `tenantId=null`, `tenant_id` ausente |
| RF-04 | mesmo IT + `mvn -f backend/pom.xml test -Dtest=FailureClassifierTest` | filho da tentativa 1: `level=ERROR`, `status_message` = rótulo classificado; filho 2 e root: `level=DEFAULT`; `status_message` não contém `at br.com` nem texto do prompt |
| RF-05 | mesmo IT | `attempt=1` no 1º filho, `attempt=2` no 2º; chave `attempt` ausente no root |
| RF-06 | `mvn -f backend/pom.xml test -Dtest=ChatTurnRecorderPortTest` | `ChatTraceRecorder.class.getDeclaredMethod("record", ChatTrace.class)` existe e não tem novos parâmetros; o root é emitido no mesmo `record` capturado pelo fake |
| RF-07 | `git diff --name-only` + `grep -rn "observation.input\|observation.output" backend/assistant/` | `ChatTrace.java` e `ChatTraceRecorder.java` fora do diff; 0 ocorrência de chaves de texto nesta fatia |
| RF-08 | IT de 2 tentativas (acima) | `roots == 1`, `filhos == 2`, `órfãos == 0`, todos com o mesmo `traceId`; 1º filho `level=ERROR` |
| NFR-01 | varredura do payload no IT | nenhum span com nome de HTTP/SQL/JVM; `max(depth) == 2`; `total_spans == 1 + tentativas` |
| NFR-02 | asserção por span no IT | todo span: `startTimeUnixNano > 0`, `endTimeUnixNano ≥ startTimeUnixNano`, `endTimeUnixNano > 0` |
| NFR-03 | teste de caracterização de chaves | conjunto de chaves `langfuse.*` é subconjunto exato da tabela "Interface congelada"; 0 chave renomeada/removida |
| NFR-04 | `grep -rn "ObservationChatModelListener" backend/assistant/ backend/provider/` | 0 ocorrência; presença de `dev.langchain4j.model.chat.listener.ChatModelListener` na instrumentação |
| NFR-05 | `grep -c '^### SPAN-' TASKS.md` + auditoria de URLs | 15 seções = 15 RF/NFR da SPEC; 0 RF/NFR órfão; toda URL de terceiro com data |
| NFR-06 | `npm test` + `git diff --name-only` | 0 falhas; 0 diff em contrato público, cota, limites, RBAC, autenticação; resposta de chat idêntica com/sem instrumentação |
| NFR-07 | IT com 2 turnos concorrentes de tenants distintos | cada span contém somente os atributos do seu turno; 0 vazamento cross-tenant/turn |

## 11. Riscos

| Risco | Mitigação | Origem no ADR |
|---|---|---|
| Propagação listener→root não funciona (escopo thread-local) | IT de 2 tentativas é critério de prova; correção no mecanismo de escopo, nunca duplicando root | "Inferência a validar", Consequência negativa |
| Mapeamento manual de `langfuse.*` diverge da interface | teste de caracterização de chaves (NFR-03) | Consequência negativa |
| Fluxo vira reativo e thread-local quebra | registrar para novo ADR; não adaptar silenciosamente nesta fatia | Consequência negativa |
| Retry gera root duplicado ou trace órfão | asserção `roots==1`/`órfãos==0` no IT (RF-08) | Decisão 1 + Inferência |
| Correlação de um turno vaza para outro | IT com 2 turnos/tenants (NFR-07) | Decisão 2 |

**Fora de escopo desta SPEC (decisões de outros ADRs):** endpoint/header/exporter OTLP (ADR-002); redação e `langfuse.observation.input/output` (ADR-003); proxy/RBAC/retention (ADR-004); Métricas/custo/`sum_totalCost` (ADR-005).

## 12. Fontes

Copiadas do `ADR.md` (consultas em 2026-11-25), sem inclusão de fonte nova:

- PRD aprovado — `.spec/prd/telemetria-ia-langfuse/PRD.md` §5 (RF1/RF2), §11 (RB2/RB6), §16 — 2026-11-25.
- RESEARCH — eixo D2 e §6 — https://langfuse.com/integrations/native/opentelemetry × 2026-11-25; https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-core/src/main/java/dev/langchain4j/model/chat/listener/ChatModelListener.java — 2026-11-25.
- Implementação `@Experimental` — https://raw.githubusercontent.com/langchain4j/langchain4j/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-25.
- Regra de propagação de atributos para todos os spans — https://langfuse.com/integrations/native/opentelemetry — 2026-11-25.
- Fatos locais (leitura em 2026-11-25): `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTraceRecorder.java:7`, `ChatTrace.java:15,45-64`, `backend/assistant/impl/src/main/java/br/com/systec/hive/assistant/impl/service/TurnOutcomeMapper.java:188`, `AssistantTurnGateway.java:117,131`, `backend/assistant/impl/src/main/java/br/com/systec/hive/assistant/impl/diagnostics/FailureClassifier.java:42`.

**Delta Specs:** nenhum. ADR-001 não altera doc vivo (`.agents/rules/**`, `AGENTS.md`) — PRD RB7.
