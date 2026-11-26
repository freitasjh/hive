---
prd_id: telemetria-ia-langfuse
title: Telemetria de IA — spans de turno e de chamada ao provider no Langfuse self-host v4
status: pendente_aprovacao_humana
prd_status: proposto
prd_version: 2
date: 2026-11-24
owner: product-manager (proposta) — aprovação: operador humano
---

# PRD — Telemetria de IA (Langfuse self-host v4)

> PICCO aprovado pelo operador em 2026-11-24 após deepsearch. Este documento é SSoT de **produto**; decisões de implementação (SDK, exporter, modelo de spans, config) pertencem ao ADR. Nenhum `ADRs/**` nasce antes da assinatura humana do §16.
>
> Co-assinatura: `engineer` (revisão de escopo/aceite) concluída em 2026-11-24. Veredito do operador: APROVADO em 2026-11-25 — ver §16.

## 1. Objetivo e problema

> ❓ Validação: problema = diagnóstico de qualidade/latência/custo do turno, não falta de log de erro.

Hoje `ChatTrace` registra uma linha por mensagem com tamanhos, parâmetros, modelo, tokens e falha — mas **sem texto de prompt/resposta e sem hierarquia entre turno e chamada ao provider** (`backend/assistant/api/.../diagnostics/ChatTrace.java:15`). Operador e engenharia não conseguem responder, em uma única consulta, "qual turno gerou esta resposta, qual modelo foi chamado, quantos tokens custou e em qual tentativa falhou". A telemetria atual não distingue retries de uma chamada ao provider nem correlaciona turno → modelo → custo.

Objetivo: primeira fatia de observabilidade de IA com **1 span por turno + 1 span por chamada ao provider** no Langfuse self-host v4, incluindo prompt/resposta em produção com **redação na origem**, RBAC e retenção; métricas operacionais permanecem em Micrometer/Prometheus e a análise de IA acontece no Langfuse. Valor: reduzir tempo de diagnóstico de incidentes de chat e permitir decisão de modelo/custo com evidência, sem transformar o trace em novo vetor de vazamento de dado.

## 2. Escopo

> ❓ Validação: inclusão/exclusão serão revisadas na assinatura do §16; detalhes técnicos ficam no ADR.

**Incluído:** ingestão de traces via OTLP/HTTP no Langfuse self-host v4; span raiz por turno e span filho por chamada ao provider (incluindo retries); atributos de correlação já existentes em `ChatTrace` (`sessionId`, `tenantId`, `turnId`, `clientId`, `promptTemplateId`, `provider`, `model`, usage, duração, falha); texto de prompt/resposta em produção com redação antes da saída do backend; RBAC e retenção no Langfuse; métricas operacionais em Micrometer/Prometheus; custo derivado pelo Langfuse a partir de usage + modelo; uso da porta `ChatTraceRecorder` existente como ponto de observação.

**Fora do escopo:** mudar contrato público da API de chat; substituir logs estruturados por trace; ingressar métricas OTLP no Langfuse; migrar para Langfuse Cloud; construir UI admin própria; alterar cota, limites `m/c/t`, RBAC da aplicação ou fluxos de autenticação; decidir SDK/exporter/configuração de deploy (ADR); implementar código, dependency, branch ou `workflow-state.json` neste PRD; criar `RESEARCH.md`/`ADR-INDEX.md`/`ADRs/**` antes do §16 assinado.

**Fora da primeira fatia (candidato a ADR posterior se o operador aprovar):** alertas v4, dashboards avançados, tracing distribuído além dos dois níveis de span.

## 3. Contexto e estado atual

- `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTraceRecorder.java:7` — porta de saída para telemetria por mensagem; adaptador de log hoje implementa `record(ChatTrace)`.
- `backend/assistant/api/src/main/java/br/com/systec/hive/assistant/api/diagnostics/ChatTrace.java:15,45-64` — record sem texto de prompt/resposta; carrega IDs de correlação, parâmetros, tokens e `failure`.
- `backend/assistant/impl/src/main/java/br/com/systec/hive/assistant/impl/service/AssistantTurnGateway.java:131` — `AiServices.builder(...)`; ponto onde o provider é efetivamente chamado (inclui retry policy em `:117`).
- `backend/dependencies/pom.xml:25,114` — Spring Boot 4.1.0 + Actuator; `:37` — LangChain4j 1.19.0. Não há registry/config de métricas explícita no código atual: forma de exposição é decisão do ADR.
- Pesquisa prévia (deepsearch, fontes em §15) indicou: endpoint OTLP/HTTP `/api/public/otel/v1/traces`, autenticação Basic `pk:sk`, header `x-langfuse-ingestion-version: 4` para ingestão em tempo real no v4; `ChatModelListener` expõe request/response com modelo e usage; o Langfuse deriva custo multiplicando preço do modelo pelo usage; alertas existem no v4.
- Decisão de produto já fixada: Langfuse **self-host v4**; prompt/resposta no trace em produção com redação na origem + RBAC/retention; primeira fatia limitada aos dois níveis de span; métricas operacionais em Micrometer/Prometheus, análise de IA no Langfuse.

## 4. Stakeholders

Operador (dono da aprovação do §16 e das premissas de privacidade/retention); `product-manager` (autor, co-assinatura com `engineer`); `architect` e `security-engineer` (pareceres de viabilidade e LGPD/segurança); times backend e QA (H1/H2/H6); operador/suporte (consumidor dos traces); usuários finais do chat (afetados se telemetria vazar texto ou alterar resposta).

## 5. Requisitos funcionais

> ❓ Validação: todo RF tem critério mensurável nesta coluna; números ausentes viram pergunta residual, não requisito inventado.

| ID | Requisito | Como medir |
|---|---|---|
| RF1 | Um turno de chat gera exatamente 1 span raiz no Langfuse, com os atributos de correlação disponíveis (`sessionId`, `tenantId`, `turnId`, `clientId`, `promptTemplateId`) quando presentes no fluxo | IT: 1 turno → 1 `traceId` com 1 observation raiz (`isRootObservation=true`) consultável na Observations API v2; 2 turnos → 2 `traceId`s distintos; nenhum atributo de correlação de outro turno no span |
| RF2 | Cada chamada ao provider, inclusive cada retry, gera 1 span filho do turno com `provider`, `model`, usage de input/output/total, duração e motivo de falha quando houver | IT com provider fake que falha na 1ª tentativa e responde na 2ª: 2 spans filhos; o 1º com `failure` não nulo; contagem de filhos = contagem de tentativas |
| RF3 | Texto de prompt e resposta entra no trace em produção somente após redação na origem, antes de sair do backend | IT/canário com fixture contendo literal sensível: payload OTLP capturado não contém o literal; trace no Langfuse contém apenas versão redigida; log do backend não contém texto bruto |
| RF4 | Regras de redação falham fechado: se o redator não puder aplicar a política, o texto não é enviado (metadados do span permanecem) | Teste com redator forçado a falhar: span existe com `provider`/`model`/usage, sem `langfuse.observation.input`/`output`; nenhum texto bruto no payload |
| RF5 | Community: acesso via auth no reverse proxy (Admin Hive + Suporte leem; time de IA administra); credencial de ingestão não serve para leitura | Teste: usuário sem papel no proxy recebe 403 na UI/API; leitura com credencial de ingestão é negada; usuário autorizado lê o trace de teste |
| RF6 | Community: retenção de 30 dias aplicada por job externo auditável, preservando traces dentro da janela | Trace artificial com 31 dias ausente após o job; trace de 29 dias presente; janela `30` registrada como parâmetro auditável em schedule verificável |
| RF7 | Métricas operacionais (volume de turnos, chamadas ao provider, falhas, latência, falhas de telemetria) são expostas em Micrometer/Prometheus, não enviadas como OTLP metrics ao Langfuse | `curl`/MockMvc em `/actuator/prometheus` contém os nomes de métricas congelados no ADR; payload OTLP de teste contém somente traces; nenhuma série de métrica no endpoint do Langfuse |
| RF8 | Custo por modelo é derivado pelo Langfuse a partir de usage + modelo, sem o backend enviar valor de custo | IT com modelo e preço de referência configurados no Langfuse e usage conhecido: Metrics API v2 com `measure=totalCost`, `aggregation=sum` retorna `sum_totalCost` > 0 igual à derivação esperada; payload OTLP não contém campo de custo calculado pelo backend |
| RF9 | Falha na ingestão de telemetria não altera resposta nem contrato do chat | IT com exporter Langfuse indisponível: resposta de chat permanece 200/contrato inalterado; contador de falha de telemetria incrementa em `/actuator/prometheus`; erro registrado em log sem texto bruto |

## 6. Requisitos não-funcionais

> ❓ Validação: privacidade, custo, retenção e vendor lock-in viram requisito verificável, não apenas prosa.

- **Privacidade/LGPD:** prompt/resposta são dado pessoal em potencial; redação na origem é condição para presença em produção. Base legal/classificação são premissas do operador, não decididas aqui. Texto bruto nunca em log, payload, métrica ou mensagem de erro.
- **Segurança:** credencial de ingestão (`pk:sk`) em cofre/secret, nunca em repo, log, trace ou resposta; transporte TLS; credencial de leitura separada da de escrita; RBAC por projeto.
- **Custo de observação:** primeira fatia limitada a 2 níveis de span; spans de infraestrutura/HTTP/SQL não entram; sampling 100% com flag de desligar por env; nenhum OTLP metrics para o Langfuse.
- **Retenção:** 30 dias, aplicada por job externo (Community) e auditável; ausência do job bloqueia implantação em produção, não é default implícito.
- **Confiabilidade:** telemetria é best-effort; falha do exporter não muda resposta ao usuário nem gera retry de chat.
- **Compatibilidade:** contrato público do chat, cota, `turnId`/`clientId` e headers de rate limit intactos; `ChatTrace` hoje não transporta texto — a forma de incluí-lo sem mudar contrato público é decisão do ADR.
- **Qualidade:** H1/H2 com suíte cheia 0 falhas; ITs reais no ponto de virada; `code-reviewer` sem 🔴; H3 se houver UI (nenhuma UI nova prevista nesta fatia).
- **Rastreabilidade e fontes:** cada RF → ADR → tarefa → evidência; afirmação sobre API de terceiro com URL + data da consulta (§15).

## 7. Restrições e premissas confirmadas

> ❓ Validação: invariantes vêm do PICCO aprovado em 2026-11-24; premissas não confirmadas ficam marcadas como pergunta residual.

Confirmadas pelo operador/PICCO: Langfuse self-host v4 **Community** (sem licença EE); prompt/resposta no trace em produção **com redação de PII+segredos na origem**; retenção de **30 dias** por job externo; acesso via proxy para Admin Hive + Suporte (time de IA administra); primeira fatia = span por turno + span por chamada ao provider; sampling 100% com flag de desligar; alertas v4 ficam para ADR posterior; métricas operacionais em Micrometer/Prometheus, análise de IA no Langfuse; Spring Boot 4.1.0 + Actuator e LangChain4j 1.19.0; teto de 5 ADRs por PRD.

Premissas de pesquisa a confirmar no ADR/RESEARCH (não são decisão de arquitetura deste PRD): transporte OTLP/HTTP em `/api/public/otel/v1/traces`, Basic `pk:sk`, header `x-langfuse-ingestion-version: 4`; `ChatModelListener` como fonte de modelo/usage; custo derivado pelo Langfuse; alertas v4 disponíveis no self-host.

**Perguntas residuais — respondidas pelo operador em 2026-11-25:** (1) redigir PII (CPF/CNPJ, e-mail, telefone) + segredos (tokens, senhas, chaves de API); (2) retenção 30 dias, job externo auditável; (3) leitura para Admin Hive + Suporte, administração pelo time de IA; (4) sampling 100% com flag de desligar por env; (5) alertas v4 FORA da primeira fatia. Nenhuma lacuna ficou sem resposta.

## 8. Alternativas consideradas

- **A (escolhida):** Langfuse self-host v4 + OTLP traces para análise de IA; Micrometer/Prometheus para métricas operacionais. Mantém dado sob controle do operador e separa os dois públicos de observabilidade.
- **B:** Langfuse Cloud — rejeitada pela decisão de self-host (residência de dado/controle de retenção); pode ser reavaliada apenas por novo PICCO.
- **C:** apenas logs estruturados + Actuator — rejeitada como solução de análise: não correlaciona turno → provider → custo nem oferece redação/RBAC/retention centralizados.
- **D:** enviar OTLP metrics ao Langfuse — rejeitada; métricas operacionais ficam em Prometheus e o endpoint do Langfuse é de traces.
- **E:** ingresso direto por SDK do Langfuse em vez de OTLP — não decidido aqui; critério de escolha e trade-off ficam no `RESEARCH.md` e no ADR.

## 9. Critérios de aceite

> ❓ Validação: comandos e verificações observáveis; itens marcados "futuro" só são executados na implementação, não neste PRD.

- Estrutura: `test -f .spec/prd/telemetria-ia-langfuse/PRD.md`; 16 seções presentes; `prd_version: 2`; §16 `Veredito: APROVADO` com data de 2026-11-25. Checagem determinística: `runPrdChecks('.')` → único achado é o órfão pré-existente `chat-model-override` (`PRD-01 FAIL`, fora de escopo); `telemetria-ia-langfuse` sem achados, `ADR-01`/`ADR-02` PASS; nenhum `ADRs/**` criado antes da assinatura.
- Futuro (após §16 assinado): `RESEARCH.md` com ≥2 alternativas, URL + data da consulta e recomendação única; `ADR-INDEX.md` 1:1 com os diretórios de ADR, 3–5 linhas, sem órfãos.
- ITs: RF1–RF4 e RF9 com provider fake e captura do payload OTLP; RF5 com usuário sem papel e com credencial de ingestão; RF6 com trace expirado e recente; RF7 com scrape de `/actuator/prometheus`; RF8 com Metrics API v2 e usage conhecido.
- Segurança: 0 literais sensíveis no payload OTLP, 0 `sk-` em log/payload, 0 texto bruto no log do backend no canário de redação; redator falhado ⇒ sem input/output no span.
- Não regressão: resposta e contrato do chat inalterados com exporter disponível e indisponível.
- Qualidade (futuro): `mvn -f backend/pom.xml test` 0 falhas; `mvn -f backend/pom.xml -Pintegration verify` 0 falhas; `code-reviewer` 0 🔴; H6 verde (brain_store + `workflow-state.json` pelo dono da esteira); portas livres. Frontend: N/A nesta fatia (nenhuma UI nova).

## 10. Histórias, riscos e mitigações

> ❓ Validação: cada história tem Dado/Quando/Então e nomeia risco + mitigação observável.

**US-01 — Diagnosticar um turno ruim em uma consulta**
- Dado que um usuário reclama de resposta ruim e existe `turnId`, quando o operador abre o trace no Langfuse, então vê span raiz do turno, spans filhos das tentativas ao provider, modelo, usage e falha, sem reconstruir logs (risco: volume/custo de observação; mitigação: só 2 níveis de span e filtro de infra, medido no payload OTLP).

**US-02 — Decidir modelo com base em custo real**
- Dado que o provider reporta usage e o Langfuse tem preço do modelo, quando a consulta de métricas é feita, então `totalCost` por modelo está disponível sem o backend calcular custo (risco: vendor lock-in no catálogo de preços; mitigação: usage e modelo também ficam em métrica Prometheus, permitindo cálculo independente).

**US-03 — Proteger prompt/resposta do usuário final**
- Dado que o texto do prompt pode conter dado pessoal ou credencial, quando o trace é exportado em produção, então o payload contém apenas texto redigido ou nenhum texto se a redação falhar (risco: LGPD/vazamento; mitigação: redação na origem, fail-closed, 0 literal sensível no canário).

**US-04 — Limitar quem lê o trace e por quanto tempo**
- Dado que o projeto Langfuse é self-host, quando um usuário sem papel tenta ler ou um trace expirado passa pela limpeza, então o acesso é negado e o trace antigo desaparece (risco: retenção indefinida/acesso indevido; mitigação: RBAC por projeto + política de retenção auditável).

**US-05 — Operar o self-host sem surpresa**
- Dado que o Langfuse self-host exige deploy, storage e upgrade, quando a primeira fatia entra em produção, então há parâmetro de volume/sampling e responsável nomeado para o serviço (risco: curva operacional e disco; mitigação: parâmetros confirmados pelo operador e documentados no ADR, nenhum default inventado neste PRD).

**US-06 — Não trocar disponibilidade do chat por telemetria**
- Dado que o exporter Langfuse pode ficar indisponível, quando uma chamada de chat acontece, então a resposta é a mesma de antes e a falha aparece só em métrica/log sem texto bruto (risco: regressão funcional; mitigação: IT com exporter falhando e contador de falha de telemetria).

## 11. Impacto em módulos e regras de negócio globais

Módulos previstos (sem código agora): `assistant/api` (porta de telemetria e contrato do dado), `assistant/impl` (ponto de captura do turno/chamada), `provider/langchain4j` (modelo/usage), configuração backend (credenciais, exporter, Actuator) e instância Langfuse self-host. Nenhuma migration de banco de domínio prevista: retenção e traces vivem no Langfuse.

Regras globais de produto (SSoT deste PRD):
- **RB1** — Texto bruto de prompt/resposta nunca sai do backend; falha do redator ⇒ descarta texto, preserva metadados.
- **RB2** — Telemetria nunca altera resposta, contrato, cota ou `turnId`/`clientId` do chat.
- **RB3** — Credencial de ingestão é somente escrita e nunca aparece em log, trace, payload ou resposta.
- **RB4** — Presença de trace em produção exige RBAC e política de retenção ativa.
- **RB5** — Métricas operacionais não são enviadas como OTLP metrics ao Langfuse; vão para Prometheus.
- **RB6** — Primeira fatia = 2 níveis de span; spans de infraestrutura fora.
- **RB7** — Nenhuma regra aqui altera `.agents/rules/**`; mudança de harness, se necessária, é demanda separada.

## 12. Priorização e métricas de sucesso

MoSCoW: **Must** — RF1, RF2, RF3, RF4, RF5, RF6, RF7, RF8, RF9 (a fatia inteira é o valor; dividir perde a correlação). **Should** — alertas v4 e dashboard de custo (se confirmados no §16). **Could** — sampling configurável ampliado. **Won't (agora)** — Langfuse Cloud, UI própria, tracing distribuído, mudança de contrato do chat.

RICE (estimativa de produto, não dado medido): Reach 3 (operador, suporte, engenharia), Impact 2 (alto — diagnóstico e custo), Confidence 0.7 (self-host v4 e redação trazem incerteza), Effort 5 (~0.84). Prioridade: **alta** — ataca perda de tempo em incidente de IA e decisão de modelo sem evidência, com risco de privacidade mitigado na origem.

KPIs: 100% dos turnos com span raiz na amostra de IT; 100% das tentativas ao provider com span filho; 0 spans órfãos na fatia; 0 literais sensíveis no payload canário; 100% de negações de acesso não autorizado; 0 traces além da retenção após limpeza; 0 falhas de suite com exporter up/down; exercício de suporte com 5 cenários localizando turno/modelo/tokens/falha em uma consulta no Langfuse.

## 13. Sequenciamento e ADRs previstos

Ordem: PRD (§16 assinado) → `RESEARCH.md` → `ADR-INDEX.md` → ADRs → implementação → VERIFY → archive. Teto respeitado: **5 ADRs candidatos**; nenhum criado agora.

| Ordem | ADR previsto | Depende de |
|---|---|---|
| 1 | ADR-001 — Contrato de telemetria: modelo de spans turno/provider, atributos de correlação e fronteira com `ChatTraceRecorder` | nenhum |
| 2 | ADR-002 — Ingestão OTLP/HTTP no Langfuse self-host v4 (endpoint, Basic `pk:sk`, header v4, isolamento do exporter) | ADR-001 |
| 3 | ADR-003 — Redação na origem e privacidade/LGPD do texto de prompt/resposta em produção | ADR-001 |
| 4 | ADR-004 — Controle de acesso no reverse proxy e retenção externa de 30 dias (Community) | ADR-002, ADR-003 |
| 5 | ADR-005 — Métricas operacionais Micrometer/Prometheus e custo derivado no Langfuse | ADR-002 |

Cada ADR = 1 branch = 1 PR = 1 revisão, merge humano; `prd_version: 2` implica `unit: adr` (`SPEC.md` + `TASKS.md`; `VERIFY.md` após merge) nas linhas `in-progress`/`merged`. Branches e diretórios só nascem após a assinatura.

## 14. Débitos registrados (fora do PRD)

- Órfão pré-existente `.spec/prd/chat-model-override/PROPOSAL.md` (sem `PRD.md`) — fora de escopo, não corrigido aqui.
- Sem baseline numérico de latência/overhead antes da telemetria: se o operador quiser meta de p95, ela entra como premissa nova, não inventada aqui.
- Alertas v4 ficaram fora da primeira fatia por decisão do operador (2026-11-25); entram como ADR posterior, não débito deste PRD.

## 15. Fontes consultadas

- Langfuse — OpenTelemetry integration / endpoint e header v4 — https://langfuse.com/integrations/native/opentelemetry — 2026-11-24.
- Langfuse — migração de ingestão customizada para v4 — https://langfuse.com/integrations/native/opentelemetry/migration-to-v4 — 2026-11-24.
- Langfuse — compatibilidade de versões (v4 GA, `x-langfuse-ingestion-version`) — https://langfuse.com/docs/compatibility — 2026-11-24.
- Langfuse — self-hosting — https://langfuse.com/self-hosting — 2026-11-24.
- Langfuse — data masking (self-host) — https://langfuse.com/self-hosting/security/data-masking — 2026-11-24.
- Langfuse — RBAC — https://langfuse.com/docs/administration/rbac — 2026-11-24.
- Langfuse — data retention — https://langfuse.com/docs/administration/data-retention — 2026-11-24.
- Langfuse — token & cost tracking (custo derivado de usage) — https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-24.
- Langfuse — spend alerts (v4+) — https://langfuse.com/docs/administration/spend-alerts — 2026-11-24.
- LangChain4j — `ObservationChatModelListener` no tag 1.19.0 (request/response, modelo, usage) — https://github.com/langchain4j/langchain4j/blob/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-24. `ChatModelListener` é a interface; a implementação de observação chama-se `ObservationChatModelListener` (alinhar no ADR).

<task>
Validar o produto da telemetria de IA: problema de diagnóstico de turno/provider/custo, primeira fatia de spans no Langfuse self-host v4, prompt/resposta com redação na origem, RBAC/retention e métricas operacionais separadas em Micrometer/Prometheus.
</task>
<context>
PICCO aprovado em 2026-11-24 após deepsearch. Stack: Spring Boot 4.1.0 + Actuator (backend/dependencies/pom.xml:25,114), LangChain4j 1.19.0 (:37), porta `ChatTraceRecorder` (assistant/api/.../ChatTraceRecorder.java:7), `ChatTrace` sem texto (assistant/api/.../ChatTrace.java:15), `AiServices` em AssistantTurnGateway.java:131. Decisões do operador: self-host v4; redação na origem + RBAC/retention; 2 níveis de span; Prometheus para métricas, Langfuse para análise. Esteira em .agents/rules/prd-adr-pipeline.md:9-16; `prd_version: 2`; máximo 5 ADRs; §16 pendente de aprovação humana.
</context>

## 16. Aprovação humana

> ❓ Validação: veredito APROVADO pelo operador em 2026-11-25; data presente; RESEARCH/ADRs liberados.

Veredito: **APROVADO** — operador confirmou PICCO e suas 6 decisões residuais em 2026-11-25.

**Status: APROVADO PELO OPERADOR.**

Data da aprovação: 2026-11-25.

- [x] Co-assinatura `engineer`: `engineer` (subagent de insumo/decisão, md-only) — 2026-11-24.
  - Parecer: escopo e aceite coerentes com o PICCO e com a pesquisa. Três esclarecimentos de aceite aplicados sem nova decisão: RF1 (`traceId` + `isRootObservation`, não "trace" na v2), RF8 (`measure=totalCost`/`aggregation=sum` → campo `sum_totalCost`) e §9 (saída esperada do `runPrdChecks` com o órfão pré-existente). Fontes: https://langfuse.com/docs/api-and-data-platform/features/public-api e https://langfuse.com/docs/metrics/features/metrics-api — 2026-11-24.
  - Ressalvas (não resolvidas aqui): (a) RBAC **por projeto** e Data Retention são **Enterprise Edition** no self-host — edição/licenciamento segue em aberto para o operador (https://langfuse.com/docs/administration/rbac; https://langfuse.com/docs/administration/data-retention — 2026-11-24); (b) custo inferido exige observation do tipo `generation` e definição de preço no self-host, a tratar no ADR (https://langfuse.com/docs/observability/features/token-and-cost-tracking — 2026-11-24); (c) a apidocs citada em §15 hoje serve o snapshot `1.25.0-beta35-SNAPSHOT`; a classe existe no tag `1.19.0` (https://github.com/langchain4j/langchain4j/blob/1.19.0/langchain4j-observation/src/main/java/dev/langchain4j/observation/listener/ObservationChatModelListener.java — 2026-11-24). Nomenclatura `ChatModelListener` (interface) × `ObservationChatModelListener` (implementação) a alinhar no ADR.
  - Pronto para submissão ao operador **com ressalvas**. Não respondi as 5 perguntas residuais (§7); co-assinatura não aprova §16, não cria ADR/RESEARCH/código e não decide arquitetura.
- [x] Aprovado por: operador (via confirmação do PICCO + respostas às 6 perguntas, 2026-11-24/25).
- [x] Data da assinatura: 2026-11-25.
- [x] Escopo aprovado: 9 RFs / 5 ADRs candidatos / teto de 5 respeitado.
- [x] Premissas respondidas (§7): PII+segredos, 30 dias, Admin Hive + Suporte / time de IA, 100% com flag, alertas v4 fora, Community + controles externos.
- [x] `prd_version: 2` confirmado no ato — ADRs `unit: adr` com `SPEC.md`/`TASKS.md` e `VERIFY.md` após merge, conforme `.agents/rules/prd-adr-pipeline.md:28-33`

## 17. Archive (2026-11-26)

- **Status:** arquivado em `.spec/archive/20261126-prd-telemetria-ia-langfuse/` após 5/5 ADRs merged (PRs #15–#19) e `VERIFY.md` APPROVED ✅ em todos.
- **Duração:** aprovado em 2026-11-25, implementado e arquivado em 2026-11-26 (~2 dias).
- **Commits de código:** `c9769fd` (001), `fc16d3b` (002), `8b5be7e` (003), `a9632a0` (004), `b5aee24` (005).
- **Evidência final:** `npm test` exit 0 (414 front / 621 back / 29 job); `mvn -Pintegration verify` 181 ITs, 0 falhas; code review APPROVED 0 🔴 em todos os 5 ADRs.
- **Delta em docs vivos:** nenhum — nenhuma alteração em `.agents/rules/**` ou `AGENTS.md` neste PRD.

**Trava:** nenhum `ADRs/**`, `RESEARCH.md`, `ADR-INDEX.md`, código, dependency, branch ou `workflow-state.json` deve nascer antes desta assinatura. Aprovação é ato humano; este documento apenas a solicita.
