<!-- scope: adr/ADR-003 -->
# SPEC — ADR-003: Redação na origem e privacidade/LGPD do texto de prompt/resposta em produção

> **Escopo:** somente a decisão congelada no `ADR.md` deste diretório. Cada `RF`/`NFR` abaixo aponta para a cláusula do ADR que o origina. Não especifica o contrato de spans (ADR-001), o transporte OTLP/header/credencial (ADR-002), RBAC/retention (ADR-004) nem a definição/registro de métricas em Micrometer (ADR-005).
>
> **Rastreabilidade:** `ADR.md` (decisão + interface congelada) → esta SPEC → `TASKS.md` (`RED-01`..`RED-18`) → evidência no `VERIFY.md` pós-merge. `depende-de: ADR-001` (filho `generation`, portador de turno e escopo do `ChatModelListener`). Status do ADR: Proposto (sem merge).
>
> **Regra de não-invenção:** esta SPEC não nomeia biblioteca, SDK, regex pronta nem assinatura de API de **terceiro** para o redator. A implementação foi decidida na emenda RED-01 do ADR (2026-11-25): **regras próprias em JDK, zero dependência nova**; CNPJ numérico **e** alfanumérico, `sk-` como requisito local e entropia como inferência sob teste. Onde aparece `RedactionPolicy`/`TextRedactor` abaixo, trata-se de nome de componente interno proposto — não de dependência decidida.

## 1. ADRs

### ADR-003 (este ciclo)

- **Decision:** redação no backend **antes** da serialização OTLP; texto capturado nos callbacks do `ChatModelListener` passa pelo redator e só a versão redigida entra no portador de turno; `langfuse.observation.input`/`output` existem **somente** no filho `generation`; política fechada com CPF, CNPJ, e-mail, telefone, tokens, senhas e chaves de API substituídos por `[REDACTED:<rule>]`, `<rule> ∈ {cpf, cnpj, email, phone, secret}`; falha/incompletude do redator ⇒ **omissão de ambas as chaves**, metadados preservados; nenhum masking EE/Collector nesta fatia; `ChatTrace` e `ChatTraceRecorder.record(ChatTrace)` inalterados. (ADR Decisão 1–6 + Interface congelada)
- **Context:** `ChatTrace.java:15` é textualmente limpo (RB1) e o masking server-side do Langfuse é EE com default **fail-open**; o masking do Collector ocorre depois de o dado ter saído da aplicação, contrariando literalmente RF3. Base legal/classificação LGPD são premissas do operador (PRD §6), não decididas aqui. (ADR Contexto)
- **Trade-offs:** literal sensível nunca deixa o processo e fail-closed é verificável por teste determinístico, contra regras que vivem no código e exigem canário contínuo; falsos positivos podem redigir texto útil e, na falha, perde-se o conteúdo da tentativa (ADR Consequências). Decisão registrada na emenda RED-01: regras próprias em JDK, zero dependência nova; fontes com URL + data em §12.

ADRs afetados: nenhum. `depende-de: ADR-001` — reusa o filho `generation`, o escopo de turno e o callback do listener; ADR-002 consome spans já redigidos; ADR-005 define o registro da métrica de falha; ADR-004 herda a superfície de texto reduzida.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `assistant` — captura e redação do texto de prompt/resposta no caminho da chamada ao provider. Compõe a telemetria de ADR-001 sem criar dependência nova entre módulos.
- **System Type:** Spring Boot 4.1.0 + LangChain4j 1.19.0. `assistant/impl` detém o redator e o portador de texto no escopo do listener; `assistant/api` detém a porta inalterada; `provider/langchain4j` entrega request/response ao callback.
- **Portador de turno ≠ `ChatTrace`:** o portador de texto é o estado do escopo de turno/criado pelo `ChatModelListener` em ADR-001, que alimenta o filho `generation`. `ChatTrace` permanece sem campos `prompt`/`response` (ADR Decisão 1/6 + Interface "Superfícies proibidas").
- **Tenant Provisioning:** N/A — nenhum tenant, sessão, cliente ou credencial é criado. IDs de correlação continuam sendo apenas lidos do escopo de turno do ADR-001.
- **Tenant Lifecycle:** N/A — ADR-003 não toca ciclo de tenant nem RBAC da aplicação (PRD RB2/RB7); redação é anterior e independente de acesso/retention (ADR-004).

## 3. Identity & Access Management

- **IdP / Protocol:** inalterados. Nenhuma autenticação do destino é modificada nesta fatia; a credencial `pk:sk` do ADR-002 não é lida, logada, redigida nem emitida aqui.
- **RBAC:** nenhuma permissão nova; `@HasPermission` existentes preservados. Texto redigido não cria novo papel de leitura — quem lê o trace é decidido no ADR-004.
- **MFA:** N/A — fora de escopo (PRD §2).
- **Sessão/cliente:** nenhum valor é gerado. `sessionId`/`tenantId`/`turnId`/`clientId`/`promptTemplateId` seguem exatamente como propagados pelo ADR-001; o redator não altera atributo de correlação.

## 4. Security, Key Management & Disaster Recovery

- **Chaves:** nenhuma chave nova. Tokens, senhas e chaves de API que apareçam **no texto do prompt/resposta** são dado a redigir (`secret`); nenhuma credencial de infraestrutura entra nesta fatia.
- **Texto bruto — superfícies proibidas:** `ChatTrace`, log do backend (inclusive `status_message` e mensagem de erro), rótulo de métrica e qualquer atributo OTLP que não seja o par `input`/`output` redigido. `sk-` não aparece em log nem payload (ADR Interface congelada + PRD RB1/§9).
- **Redação:** aplicada antes de o texto entrar no portador de turno e, portanto, antes da serialização OTLP do ADR-002. O exportador recebe somente texto redigido ou nenhum texto.
- **Fail-closed:** exceção, timeout/expiração, indisponibilidade do redator ou aplicação incompleta da política ⇒ omissão de **ambas** as chaves de texto; `provider`, `model`, `usage_details`, `attempt` e `level` permanecem (ADR Decisão 4).
- **LGPD:** classificação e base legal seguem como premissa do operador (PRD §6/§7); este ADR não as decide. A mitigação técnica é a redação na origem + canário.
- **RTO/RPO:** N/A — nenhum estado persistido novo; retention pertence ao ADR-004.

## 5. Data Model & Concurrency

- Nenhuma entidade, tabela, migration Flyway/Liquibase ou chave Redis é criada ou alterada. Texto redigido vive no atributo do span filho `generation` (`langfuse.observation.input`/`output`), não em banco de domínio (ADR Decisão 2).
- **ERD:** N/A — sem entidade nova neste ciclo (PRD §11: traces vivem no Langfuse).
- **Migrations:** N/A — sem migration Java neste ciclo.
- **Concorrência:** o redator é uma função pura sobre o texto recebido; estado de falha é por evento de turno/tentativa, nunca global entre turnos. Um evento fail-closed não suprime texto de outro turno. A mecânica de escopo é a do ADR-001 (thread-local por turno); virada reativa exige novo ADR, não adaptação silenciosa.

## 6. Event Model, Integrations & Messaging Isolation

- **Event Registry:** N/A — nenhum evento de domínio novo. Atributos OTLP não são evento de domínio; exportação pertence ao ADR-002.
- Integração de entrada: request/response do `ChatModelListener` (ADR-001). Integração de saída: o par de atributos congelados no filho `generation`, consumido pelo exporter do ADR-002 já redigido.
- **Isolamento:** nenhum texto de um turno pode aparecer no span de outro; redator não replica, não caches e não compartilha buffer entre turnos. A métrica de falha é agregado global por construção (contador), sem texto (ADR Interface "Métrica de falha").

## 7. Invariants & Property-Based Testing

### 7.1 Invariantes de redação

| ID | Propriedade | Verificação |
|---|---|---|
| INV-1 | Todo texto que entra no portador de turno passou pelo redator; 0 caminho de bypass | teste de instrumentação com fixture sensível + varredura do payload protobuf decodificado |
| INV-2 | `langfuse.observation.input`/`output` existem somente no filho `generation`; root sem ambas as chaves | asserção por span no payload OTLP protobuf decodificado (ou via Observations API v2) |
| INV-3 | Placeholder sempre `[REDACTED:<rule>]`, `<rule> ∈ {cpf, cnpj, email, phone, secret}`; 0 outro formato | teste de caracterização do conjunto de placeholders |
| INV-4 | Fail-closed é atômico: redator falhou ⇒ ambas as chaves ausentes; `provider`, `model`, `usage_details`, `attempt`, `level` presentes | teste com redator forçado a falhar |
| INV-5 | Texto bruto em 0 superfície proibida (ChatTrace, log, métrica, mensagem de erro, qualquer atributo OTLP fora do par) | `grep` no log + asserção sobre payload protobuf decodificado + `git diff` |
| INV-6 | 1 incremento de `chat.telemetry.redaction.failures.total` por evento fail-closed; rótulo sem texto | leitura do registry Micrometer no teste |
| INV-7 | `ChatTrace`, `ChatTraceRecorder.record(ChatTrace)` e contrato público do chat inalterados, com redator ativo ou falhando | reflection + `git diff --name-only` + comparação de resposta |
| INV-8 | 0 mecanismo de masking EE/Collector/hook SDK nesta fatia | varredura estática de config/código |

### 7.2 Invariantes de workflow

- **Sucesso:** callback do listener → redator → portador de turno → atributo redigido no filho `generation` → exportador (ADR-002). Texto bruto não sobrevive a nenhum degrau.
- **Falha do redator:** exceção/timeout/indisponibilidade/política incompleta → nenhuma chave de texto → metadados do filho permanecem → contador de falha incrementado → log sem texto. Nunca retry da chamada de chat e nunca propagação para a resposta (RF9/ RB2).
- **Root:** nunca recebe `input`/`output`, em sucesso nem em falha.

### 7.3 Restrições de geração de dados de teste

| Campo | Tipo | Restrição | Objetivo |
|---|---|---|---|
| `promptText`/`responseText` | String | fixture contém `123.456.789-09`, `12.345.678/0001-90` (CNPJ numérico), **`12.ABC.345/01DE-35` (CNPJ alfanumérico válido — 12 posições alfanuméricas + DV `35` calculado pela tabela oficial)**, `joao.silva@example.com`, `(11) 98765-4321`, `sk-abcdef1234567890`, `senha=SuperSecret123` | provar RF3 com literal sensível conhecido, inclusive CNPJ alfanumérico (jul/2026) |
| `redactorOutcome` | enum | `SUCCESS` ou `FORCED_FAILURE` (exceção, timeout e política incompleta como variações) | provar RF4/canário de falha |
| `rule` | enum fechado | `cpf\|cnpj\|email\|phone\|secret` | nenhum placeholder inventado |
| `spanKind` | enum | `ROOT` ou `GENERATION_CHILD` | texto só no filho |
| `chunk` | Map<String,String> | sempre contém `provider`, `model`, `usage_details`, `attempt`, `level` | fail-closed degrada para metadados |

## 8. Requisitos Funcionais

- **RF-01 — Portador de texto fora da porta.** Capturar prompt/resposta nos callbacks do `ChatModelListener` em um portador de turno distinto de `ChatTrace`; `ChatTrace` e `ChatTraceRecorder.record(ChatTrace)` permanecem inalterados. *(ADR Decisão 1/6 + Interface "Superfícies proibidas"; RF3 do PRD.)*
- **RF-02 — Redação antes da serialização OTLP.** Aplicar o redator ao texto antes de ele entrar no portador de turno; o exportador (ADR-002) recebe spans já redigidos, nunca texto bruto. *(ADR Decisão 1; RF3 do PRD.)*
- **RF-03 — Texto somente no filho `generation`.** Preencher `langfuse.observation.input`/`output` apenas no span de tentativa ao provider; root permanece sem ambas as chaves. *(ADR Decisão 2 + Interface "Atributos de texto"/"Root"; RF3 do PRD.)*
- **RF-04 — Regras de PII.** CPF, CNPJ, e-mail e telefone são substituídos respectivamente por `[REDACTED:cpf]`, `[REDACTED:cnpj]`, `[REDACTED:email]`, `[REDACTED:phone]`. CNPJ cobre **numérico e alfanumérico**; regex só-números é proibida (gov.br jul/2026 vence sobre doc do HV). *(ADR Decisão 3 + Interface "Formato de CNPJ"; emenda RED-01.)*
- **RF-05 — Regras de segredos.** Tokens, senhas e chaves de API são substituídos por `[REDACTED:secret]`, incluindo literais com prefixo `sk-` (**requisito local**, sem fonte externa). Fallback por entropia é **inferência**, só entra se teste/IT dedicado validar e nova emenda registrar. *(ADR Decisão 3 + Interface "Prefixo `sk-`"/"Fallback de entropia"; emenda RED-01.)*
- **RF-06 — Placeholder de conjunto fechado.** Nenhum outro placeholder é produzido ou aceito downstream; `<rule>` pertence exclusivamente a `{cpf, cnpj, email, phone, secret}`. *(ADR Decisão 3 + Interface "Placeholder".)*
- **RF-07 — Fail-closed atômico.** Se o redator lançar, expirar, estiver indisponível ou a política não puder ser aplicada integralmente, omitir ambas as chaves de texto e preservar `provider`, `model`, `usage_details`, `attempt` e `level`. *(ADR Decisão 4 + Interface "Fail-closed"; RF4 do PRD.)*
- **RF-08 — Superfícies proibidas.** Texto bruto não vai para `ChatTrace`, log do backend, rótulo de métrica, mensagem de erro nem qualquer atributo OTLP fora do par redigido; 0 `sk-` em log/payload (payload inspecionado após decodificação protobuf). *(ADR Decisão 4 + Interface "Superfícies proibidas"; RB1.)*
- **RF-09 — Métrica de falha.** Incrementar `chat.telemetry.redaction.failures.total` uma vez por evento fail-closed, sem texto bruto em rótulo. Definição/registro Micrometer pertence ao ADR-005; ADR-003 fixa só o nome e a semântica do incremento. *(ADR Interface "Métrica de falha".)*
- **RF-10 — Canário de payload protobuf decodificado.** Com fixture contendo literal sensível: payload OTLP protobuf capturado, após decodificação (`ExportTraceServiceRequest`/`resourceSpans`) ou validado pela Observations API v2, não contém o literal; trace no Langfuse contém somente a versão redigida; log do backend não contém texto bruto. Nenhuma asserção lê JSON cru. *(ADR "Critério de canário"; RF3 do PRD/§9; decisão de encoding do ADR-002, 2026-11-25.)*
- **RF-11 — Canário com redator forçado a falhar.** Span existe com `provider`/`model`/usage e sem `langfuse.observation.input`/`output`; nenhum texto bruto no payload protobuf decodificado (ou na Observations API v2). *(ADR "Critério de canário"; RF4 do PRD/§9.)*
- **RF-12 — Sem masking EE/Collector.** Nenhuma variável de masking EE, nenhum Collector e nenhum hook de SDK é introduzido nesta fatia; defesa em profundidade posterior não substitui a redação na origem. *(ADR Decisão 5 + Alternativas descartadas.)*
- **RF-13 — Contrato do chat inalterado.** Com redator ativo e com redator falhando, resposta, cota, limites `m/c/t`, `turnId`/`clientId` e headers de rate limit permanecem idênticos. *(ADR Decisão 6; RB2.)*

## 9. Requisitos Não-Funcionais

- **NFR-01 — Nomenclatura congelada.** Chaves `langfuse.observation.input`/`output`, formato `[REDACTED:<rule>]` e conjunto de regras são imutáveis neste e nos ADRs downstream; root nunca ganha as chaves. *(ADR Interface congelada.)*
- **NFR-02 — Fail-closed por construção.** Nenhuma variável de ambiente ou config habilita fail-open; indisponibilidade do redator resulta em omissão de texto, nunca em envio do original. O default fail-open do mecanismo EE é justamente o motivo do descarte. *(ADR Decisão 4 + Alternativas descartadas; fonte S4 — 2026-11-25.)*
- **NFR-03 — Rastreabilidade e fontes.** Cada `RF`/`NFR` desta SPEC vira exatamente 1 task em `TASKS.md`; toda afirmação sobre API de terceiro cita URL + data da consulta. *(ADR Fontes; RESEARCH §10.)*
- **NFR-04 — Não regressão.** Suíte completa 0 falhas; 0 diff em `ChatTrace.java`, `ChatTraceRecorder.java`, contrato público, cota, limites, RBAC e autenticação; frontend N/A nesta fatia (nenhuma UI nova). *(ADR Decisão 6; PRD RB2/§9.)*
- **NFR-05 — Redator sem biblioteca inventada / decisão registrada.** Registrada a decisão da emenda RED-01 (2026-11-25): regras próprias em JDK (`Pattern.compile`/`Matcher.find` + dígitos verificadores), **zero dependência nova no `pom`**. DLP externo, Phileas, libphonenumber e Commons Validator foram descartados com fonte; `sk-` é requisito local e entropia é inferência sob teste. Qualquer dependência nova exige nova emenda com URL + data — não pode ser inferida nesta fatia. *(Emenda RED-01; protocolo deepsearch; RESEARCH D3.)*

## 10. Critérios de Aceite (executáveis)

Comando canônico de suíte: `npm test` (`harness.config.json.commands.suiteFull`). Comandos de backend: `mvn -f backend/pom.xml test` e `mvn -f backend/pom.xml -Pintegration verify`. `PAYLOAD` = payload OTLP **protobuf** (`Content-Type: application/x-protobuf`) capturado pelo fixture de teste e inspecionado **decodificado** (`ExportTraceServiceRequest`/`resourceSpans`) ou, alternativamente, validado pela Observations API v2 — nunca JSON cru (decisão do ADR-002, 2026-11-25). `LOG` = saída do log do backend no mesmo teste. ITs de canário exigem `HIVE_TELEMETRY_ENABLED=true` explicitamente: o default é `false` (opt-in, ADR-002).

**Estratégia de testes:** (1) unitários — política de redação, placeholder, fail-closed e métrica, com o fixture sensível acima; (2) integração — canário no payload protobuf decodificado e no trace via Observations API v2, com provider fake; (3) canário determinístico — redator forçado a falhar; (4) estáticos — ausência de config EE/Collector e de diff em arquivos proibidos. Toda task em `TASKS.md` escreve o teste antes da implementação.

| Req | Task | Comando / verificação | Asserção observável |
|---|---|---|---|
| RF-01 | RED-02 | `mvn -f backend/pom.xml test -Dtest=TextCarrierListenerTest` + `git diff --name-only -- backend/assistant/api/` | callback `onRequest`/`onResponse` alimenta portador distinto de `ChatTrace`; 0 diff em `ChatTrace.java`/`ChatTraceRecorder.java` |
| RF-02 | RED-06 | `mvn -f backend/pom.xml test -Dtest=RedactionBeforeExportTest` | texto lido no portador já é redigido; 0 ocorrência do literal no objeto entregue ao exporter |
| RF-03 | RED-07 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=GenerationChildTextIT` | `input`/`output` presentes no filho `generation`; root sem ambas; `count(chaves de texto no root) == 0` |
| RF-04 | RED-03 | `mvn -f backend/pom.xml test -Dtest=RedactionPolicyTest` | fixture ⇒ `[REDACTED:cpf]`, `[REDACTED:cnpj]`, `[REDACTED:email]`, `[REDACTED:phone]`; **CNPJ numérico `12.345.678/0001-90` e alfanumérico `12.ABC.345/01DE-35` ambos redigidos**; 0 literal original na saída |
| RF-05 | RED-04 | mesmo teste, caso `secret` | `sk-abcdef1234567890` e `senha=SuperSecret123` ⇒ `[REDACTED:secret]`; 0 `sk-` na saída; entropia **fora do contrato**, só sob teste dedicado |
| RF-06 | RED-05 | `mvn -f backend/pom.xml test -Dtest=PlaceholderContractTest` | conjunto de placeholders == subconjunto de `{cpf,cnpj,email,phone,secret}`; 0 formato diverso |
| RF-07 | RED-08 | `mvn -f backend/pom.xml test -Dtest=FailClosedRedactorTest` | exceção, timeout e política incompleta ⇒ ambas as chaves ausentes; `provider`/`model`/`usage_details`/`attempt`/`level` presentes |
| RF-08 | RED-10 | `mvn -f backend/pom.xml test -Dtest=NoRawTextSurfaceTest` + `grep -RInF 'sk-abcdef1234567890' $LOG` | 0 ocorrência no `$LOG` e nos atributos do payload protobuf decodificado; nenhuma chave de texto fora do par redigido |
| RF-09 | RED-09 | `mvn -f backend/pom.xml test -Dtest=RedactionFailureMetricTest` | 1 fail-closed ⇒ `chat.telemetry.redaction.failures.total` +1; rótulo sem literal sensível |
| RF-10 | RED-11 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=RedactionCanaryIT` com `HIVE_TELEMETRY_ENABLED=true` — decode protobuf (`ExportTraceServiceRequest`/`resourceSpans`) **ou** Observations API v2 | `Content-Type: application/x-protobuf` no capture; 0 literal do fixture em atributos decodificados e em `$LOG`; trace contém só `[REDACTED:...]`. Nenhuma asserção sobre JSON cru |
| RF-11 | RED-12 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=ForcedRedactorFailureIT` com `HIVE_TELEMETRY_ENABLED=true` — payload protobuf decodificado ou Observations API v2 | filho existe com `provider`/`model`/usage; `input`/`output` ausentes; 0 texto bruto |
| RF-12 | RED-13 | `grep -RIn 'LANGFUSE_INGESTION_MASKING_CALLBACK_FAIL_CLOSED\|mask_otel_spans\|LangfuseSpanProcessor\|opentelemetry-collector' backend/` | 0 ocorrência |
| RF-13 | RED-14 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=ChatContractWithRedactionTest` | resposta/headers/cota idênticos com redator ativo e forçado a falhar |
| NFR-01 | RED-15 | `mvn -f backend/pom.xml test -Dtest=FrozenRedactionNamingTest` | chaves, placeholder e regras exatamente como na Interface congelada; 0 rename/remoção |
| NFR-02 | RED-16 | `mvn -f backend/pom.xml test -Dtest=FailOpenConfigAbsenceTest` + `grep -RIn 'fail.open\|FAIL_OPEN' backend/assistant/` | nenhum caminho de config envia texto original; 0 flag fail-open |
| NFR-03 | RED-17 | `grep -c '^### RED-' TASKS.md` + auditoria de URLs | 18 seções ↔ 18 RF/NFR (13 RF + 5 NFR); 0 órfão; toda URL de terceiro com data |
| NFR-04 | RED-18 | `npm test` + `git diff --name-only` | 0 falhas; 0 diff em arquivos proibidos; frontend N/A |
| NFR-05 | RED-01 | `git diff --name-only -- backend/pom.xml` + `grep -n 'Emenda RED-01' ADR.md` | 0 diff em `pom.xml` (zero dependência nova); emenda RED-01 registrada com URL + data 2026-11-25 |

## 11. Riscos

| Risco | Mitigação | Origem no ADR |
|---|---|---|
| Falso positivo redigindo texto útil | canário contínuo + regras fechadas; divergência volta ao `architect` | Consequência negativa |
| Redator falha e perde conteúdo daquela tentativa | degrada para metadados úteis + contador + log sem texto (RF-07/RF-09) | Consequência negativa |
| Literal sensível escapa por superfície não prevista (log/mensagem de erro) | varredura de superfícies proibidas no canário (RF-08/RF-10) | Interface congelada |
| CNPJ alfanumérico vazado por regex só-números | emenda RED-01 + fixture alfanumérico em RED-03 (gov.br jul/2026) | Emenda RED-01; Deepsearch 2026-11-25 |
| Entropia promovida a contrato sem evidência | inferência rotulada; só entra com teste/IT e nova emenda (RF-05/RED-04) | Emenda RED-01 |
| `sk-` citado como padrão de terceiro | tratado como requisito local da SPEC (RF-05), nunca como fato externo | Emenda RED-01 |
| Texto parcial: só `input` redigido e `output` bruto (ou vice-versa) | fail-closed atômico remove ambas as chaves (INV-4) | Decisão 4 |
| Root herda texto do filho por compartilhamento de escopo | asserção `count(chaves no root)==0` (RF-03) | Decisão 2 |

**Fora de escopo desta SPEC (decisões de outros ADRs):** contrato de spans/atributos de correlação (ADR-001); endpoint, Basic `pk:sk`, header v4 e exporter (ADR-002); proxy/RBAC/retention (ADR-004); definição/registro de `chat.telemetry.redaction.failures.total` em Micrometer e custo (ADR-005); classificação LGPD/base legal (operador — PRD §6).

## 12. Fontes e decisão registrada

Copiadas do `ADR.md` (consultas em 2026-11-25), mais a nota de compatibilidade upstream do ADR-002 abaixo (decisão do `architect` em 2026-11-25, sem alteração daquele ADR):

- Langfuse — masking (na aplicação vs Collector vs SDK; fail-closed do SDK) — https://langfuse.com/docs/observability/features/masking — 2026-11-25.
- Langfuse — self-host data masking (EE, `...FAIL_CLOSED=false`, só `/api/public/otel`) — https://langfuse.com/self-hosting/security/data-masking — 2026-11-25.
- PRD — §5 (RF3/RF4), §6 (privacidade/LGPD), §7 (respostas do operador), §9 (canário), §11 (RB1/RB2) — 2026-11-25.
- RESEARCH — eixo D3 — 2026-11-25.
- ADR-002 (`ADR.md` Decisão 1 + Interface "Encoding"; decisão do `architect` em 2026-11-25): OTLP/HTTP protobuf, `Content-Type: application/x-protobuf`; canário da ADR-003 lê payload **decodificado** ou usa Observations API v2. Fontes herdadas: https://langfuse.com/integrations/native/opentelemetry — 2026-11-25; https://github.com/open-telemetry/opentelemetry-java/issues/5598 — 2023-07-06 (consultado 2026-11-25). Flag `HIVE_TELEMETRY_ENABLED` default `false` (opt-in) também do ADR-002.

- Oracle — JDK 25 `Pattern`/`Matcher` (imutabilidade e thread-safety de `Pattern`) — https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html — 2026-11-25.
- Receita Federal — CNPJ Alfanumérico e cronograma (produção 27/07/2026; primeiro CNPJ 31/07/2026) — https://www.gov.br/receitafederal/pt-br/acesso-a-informacao/acoes-e-programas/programas-e-atividades/cnpj-alfanumerico — 2026-11-25; primeiro CNPJ emitido — https://www.gov.br/receitafederal/pt-br/assuntos/noticias/2026/julho/receita-federal-gera-o-primeiro-cnpj-em-formato-alfanumerico — 2026-11-25; cálculo do DV (módulo 11, ASCII−48) — https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf — 2026-11-25.
- Hibernate Validator — Reference Guide 9.2 (`@CNPJ` "starting January 2026", divergente da Receita) — https://docs.hibernate.org/validator/9.2/reference/en-US/html_single — 2026-11-25; Migration Guide 9.2 (CPF completo ou 11 dígitos) — https://docs.hibernate.org/validator/9.2/migration/en-US/html_single/ — 2026-11-25.
- Deepsearch do `engineer` (nota canônica no Brain; 22 fontes; Context7 indisponível) — `estudos/projetos/hive/adr-003-004-deepsearch-redacao-retencao-2026-11-25` — 2026-11-25.

**Decisão registrada (emenda RED-01, 2026-11-25):** regras próprias em JDK, zero dependência nova; CNPJ numérico e alfanumérico; `sk-` requisito local; entropia é inferência, só entra com teste/IT dedicado e nova emenda. `RED-01` verifica o registro (URL + data e 0 diff em `pom.xml`); nenhuma dependência nova entra sem nova emenda.

**Delta Specs:** nenhum. ADR-003 não altera doc vivo (`.agents/rules/**`, `AGENTS.md`) — PRD RB7. O nome `chat.telemetry.redaction.failures.total` é apenas referenciado; sua definição em Micrometer é do ADR-005.

## 13. Score do portão (spec-gate)

Itens das seções 1–4 de `.agents/skills/sdd-compiler/asserts/spec-gate.md` (11 itens):

| # | Item | Resultado |
|---|---|---|
| 1 | ADRs (decisão + porquê) | ✅ §1 (emenda RED-01 registrada com URL + data) |
| 2 | DDD Modular (api/impl/public) | ✅ §2 — `api` inalterada; redator e portador em `impl` |
| 3 | Segurança/IAM + capacidades | N/A — nenhuma autenticação/permissão nova; credencial do ADR-002 não é lida (§3) |
| 4 | ERD | N/A — sem entidade nova neste ciclo (§5) |
| 5 | Event Registry | N/A — nenhum evento de domínio novo; atributos OTLP não são evento (§6) |
| 6 | Migrations | N/A — sem migration Java neste ciclo (§5) |
| 7 | Invariantes de entidade | ✅ §7.1 (INV-1..INV-8 sobre redator, placeholder, fail-closed e superfícies) |
| 8 | Invariantes de workflow | ✅ §7.2 |
| 9 | Test Strategy | ✅ §10 (unitários, integração, canário determinístico, estáticos) |
| 10 | Single File | ✅ arquivo único |
| 11 | Domínio claro | ✅ `assistant` |

`itens aplicáveis = 7` (4 N/A com motivo); `atendidos = 7` ⇒ **score = 100%**. Gaps `🔴`/`🟠`: nenhum. `frontend_evidence`: N/A — nenhuma UI nova nesta fatia.
