---
prd_id: telemetria-ia-langfuse
adr_id: ADR-004
title: "Controle de acesso no reverse proxy e retenção externa de 30 dias (Community)"
status: Proposto
data: 2026-11-25
depende-de: "ADR-002, ADR-003"
rf: "RF5, RF6"
type: adr
---

# ADR-004 — Controle de acesso no reverse proxy e retenção externa de 30 dias

- **Data:** 2026-11-25
- **Status:** Proposto — decisão congelada; sem merge (`ADR-INDEX.md`: `todo`).
- **Emenda 2026-11-25 (ACC-23):** executor decidido por deepsearch — CLI Java standalone em container, cron do host via Docker Compose, **fora do backend**. Ver §"Emenda ACC-23".
- **Decisões operacionais do operador (2026-11-25, 4 respostas):** plataforma = Docker Compose + cron do host; auditoria = stdout → agregador (JSON por linha); cutoff = `startTime` do root (determinístico, reexecutável); campo de contagem = `submitted` (renomeado em 2026-11-25), prova real por re-leitura v2. Ver §"Decisões operacionais registradas".
- **depende-de:** ADR-002 (credencial/endpoint de ingestão), ADR-003 (texto redigido no trace).
- **RFs atendidos:** RF5 (acesso/credencial de ingestão sem leitura), RF6 (30 dias por job auditável).

## Contexto

Decisão congelada do operador: Langfuse **self-host v4 Community, sem EE**; leitura para Admin Hive + Suporte, administração pelo time de IA; retenção de 30 dias por job externo auditável. No self-host, **RBAC por projeto e Data Retention nativa são Enterprise Edition**; sem política, o Langfuse self-host guarda eventos indefinidamente. API key com permissão `Application` envia traces; `Viewer` é somente leitura.

## Decisão

1. **Autenticação/autorização no reverse proxy**, na frente da UI e da API do Langfuse. Capacidades lógicas: `telemetry.read` para Admin Hive e Suporte; `telemetry.admin` para o time de IA. Sem capacidade ⇒ **403**. Signup e login por usuário/senha desabilitados; endpoints de administração só pela rede interna do proxy; TLS obrigatório.
2. **Proxy ≠ RBAC por projeto.** O proxy controla quem chega à instância; não cria papéis por projeto dentro do Langfuse. Na primeira fatia existe **um projeto** e nenhuma promessa de isolamento entre projetos. Se surgir necessidade de RBAC por projeto, isso reabre licença/edição — não é "o mesmo controle" (RB4 lido com fonte).
3. **Chaves separadas por finalidade:** chave de ingestão com permissão `Application` (escrita de traces, sem leitura) usada somente pelo exporter do ADR-002; chave `Viewer` para leitura programática, guardada em cofre e não entregue ao backend; **terceira chave, com permissão de delete, usada somente pelo job** — nunca pelo backend. A doc lista `Full access` como "all read and write" e não define um escopo exclusivo de delete; portanto **que a chave de delete deva ser `Full access` é INFERNÊNCIA**, a confirmar por IT de 403 (detalhes em §"Emenda ACC-23"). Nenhuma credencial em log, trace, payload ou resposta (RB3).
4. **Retenção por job externo auditável, janela 30 dias.** Seleção: observations do tipo root (`isRootObservation=true`) com `startTime` anterior ao cutoff determinístico `now - retention_days` (**`startTime` do root**; reexecutável com o mesmo limite), via Observations API v2 (`fromStartTime` **inclusivo**, `toStartTime` **exclusivo**, `fields=core,basic` — `isRootObservation` está em `basic`, não em `core`; paginação por `meta.cursor`, `limit` ≤ 1000); agrupar por `traceId`; exclusão em lote por `DELETE /api/public/traces` com corpo `{"traceIds":[…]}` (lotes de 1..1000 IDs; resposta `{"message":…}`) usando chave de delete. **Executor: CLI Java standalone, fora do backend**, com `java.net.http.HttpClient` (JDK) + Jackson, containerizada e disparada por **cron do host via Docker Compose** (plataforma confirmada pelo operador em 2026-11-25; o repo já tem `docker/docker-compose-*.yml`; nenhum manifest k8s foi encontrado). Lock + cutoff determinístico para reexecução segura. Auditoria em **JSON estruturado em stdout, uma linha por evento, coletado por agregador** (destino confirmado pelo operador em 2026-11-25). Job recorrente; **dono = time de IA e cadência = diário às 03:00** (decisão operacional de 2026-11-25). Sem job ativo, bloquear implantação em produção (PRD §6).
5. Traces com 29 dias permanecem; traces com 31 dias desaparecem após o job. Exclusão é irreversível; `submitted` no log é **contagem de IDs submetidos**, não confirmação de exclusão — a prova real exige re-leitura pela Observations API v2 (a exclusão é assíncrona e não há notificação de confirmação). Detalhes em §"Emenda ACC-23".

### Blueprint de contrato ( API de leitura/cleanup )

| Operação | Contrato |
|---|---|
| Leitura de roots expired | `GET /api/public/v2/observations?isRootObservation=true&fromStartTime=<anterior>&toStartTime=<cutoff>&fields=core,basic`; paginar com `meta.cursor` (`limit` ≤ 1000); continuar enquanto houver cursor, mesmo com `data` vazio |
| Exclusão em lote | `DELETE /api/public/traces` corpo `{"traceIds":[…]}` (1..1000 IDs por lote) → `{"message":…}` |
| Parâmetro auditável | `retention_days=30` |
| Negação de acesso | 403 no proxy para ausência de `telemetry.read`/`telemetry.admin`; credencial `Application` nunca lê trace |

## Interface congelada

| Nome congelado | Valor / comportamento fixado |
|---|---|
| Capacidades do proxy | `telemetry.read`, `telemetry.admin` (prefixo e grafia exatos); sem capacidade ⇒ 403 |
| Escopo da chave de ingestão | `Application` — somente envio de traces |
| Escopo da chave de leitura | `Viewer` — somente leitura; separada da de ingestão |
| Chave de delete | Terceira chave, exclusiva do job, **nunca no backend**. Permissão `Full access` é **INFERNÊNCIA** (doc só diz "all read/write"); confirmar com IT de 403 antes de produção |
| Executor do job | CLI Java standalone (`java.net.http.HttpClient` do JDK + Jackson), containerizada, disparada por cron do host via Docker Compose (confirmado em 2026-11-25); **fora do backend** e fora do proxy; sem chave de delete no backend |
| Janela de retenção | `retention_days=30` (parâmetro auditável; mínimo nativo EE é 3, mas Community usa job externo) |
| Critério de seleção | `isRootObservation=true`, `fromStartTime` inclusivo, `toStartTime=<cutoff>` exclusivo com **cutoff = `startTime` do root** (determinístico/reexecutável, 2026-11-25), `fields=core,basic`; agrupar por `traceId`; paginar por `meta.cursor` até cursor ausente |
| Endpoint de exclusão | `DELETE /api/public/traces`, corpo `{"traceIds":[…]}`, 1..1000 IDs por lote |
| Semântica de `submitted` | Contagem de IDs **submetidos** (nome congelado em 2026-11-25), não confirmação; prova de exclusão exige re-leitura v2 |
| Formato de auditoria | JSON estruturado em **stdout, uma linha por evento**, coletado por **agregador** (2026-11-25), sem secrets/PII e sem CR/LF injetados (OWASP) |
| Campos de auditoria do job | `run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors` (congelados) + `traceIds`, `window`, `timestamp` (decisão operacional de 2026-11-25) |
| Equivalência proibida | "auth no proxy" **nunca** chamado de "RBAC por projeto" |

> **Decisão operacional do operador (2026-11-25):** dono = **time de IA**; cadência = **diário às 03:00**; plataforma = **Docker Compose + cron do host**; auditoria = **stdout → agregador, JSON por linha**, com `traceIds` submetidos, janela e timestamp; cutoff = **`startTime` do root** (determinístico, reexecutável); campo de contagem = **`submitted`**, prova real por re-leitura v2. Contrato congelado acima inalterado.

## Emenda ACC-23 — decisão do executor (deepsearch 2026-11-25)

**Gate:** `ACC-23` (`SPEC.md` §12, NFR-06). Deepsearch do `engineer` em 2026-11-25, nota `estudos/projetos/hive/adr-003-004-deepsearch-redacao-retencao-2026-11-25` (22 fontes; websearch + webfetch em fontes oficiais). **Context7: indisponível** (sem MCP tool e sem CLI `ctx7`/`context7`) — declarado, não substituído por memória. Esta emenda não altera SPEC/TASKS (o `engineer` as atualiza depois) e não adiciona código.

### Decisão

1. **CLI Java standalone, fora do backend.** `java.net.http.HttpClient` (JDK) para chamadas HTTP + Jackson para JSON; containerizada; disparada por **cron do host via Docker Compose**. Evidência local: `docker/docker-compose-vetor.yml` e `docker/docker-compose-redis.yml` existem; **nenhum manifest Kubernetes foi encontrado no repo**. Lock + cutoff determinístico dão idempotência a reexecuções com o mesmo `run_id`. **Plataforma confirmada pelo operador em 2026-11-25**: Docker Compose + cron do host. Fonte: Oracle JDK 25 `HttpClient` — 2026-11-25; evidência local do repo — 2026-11-25.
2. **Separação de deveres preservada.** A chave de delete fica **somente no job**. O backend continua com `Application` (escrita) e nunca recebe `Viewer` nem a chave de delete; nem `@Scheduled`, nem Spring Batch, nem qualquer agendador in-process no backend. Fonte: Langfuse RBAC (permissões de API key imutáveis após criação) — 2026-11-25.
3. **Contrato de leitura verificado.** `GET /api/public/v2/observations`: `fromStartTime` **inclusivo**, `toStartTime` **exclusivo**; `fields=core,basic` obrigatório porque **`isRootObservation` pertence a `basic`, não a `core`**; `limit` default 50 / máx 1000; `meta.cursor` pagina **por cursor, não por offset** — continuar enquanto houver cursor, mesmo que `data` venha vazio. Ordenação por `startTime` desc; self-host sem rate limit forçado na leitura. Ressalva oficial: o filtro de root lógico assume **um root exportado por trace**; trace sem root exportado no projeto não casa — agrupar por `traceId`. O operador confirmou em 2026-11-25 que o cutoff é o **`startTime` do root**, determinístico e reexecutável com a mesma janela. Fontes: Langfuse Observations API / Public API — 2026-11-25.
4. **Contrato de exclusão verificado.** `DELETE /api/public/traces`, corpo `{"traceIds":[…]}` com 1..1000 IDs, resposta 200 `{"message":…}`; ação `traces:delete`, rate limit `trace-delete`; o servidor registra auditLog `deleteTraces` com `traceIds` e `apiKeyId`. Fontes: Langfuse Data Deletion e OpenAPI spec — 2026-11-25.
5. **`submitted` não é prova.** A resposta de delete não informa quantidade realmente removida; a exclusão de tracing é assíncrona (usualmente até ~15 min) e **não há confirmação nem notificação**. Logo `submitted` = nº de IDs **submetidos** (nome do campo congelado pelo operador em 2026-11-25); a verificação real é re-leitura pela Observations API v2. Fonte: Langfuse Data Deletion (limitações) — 2026-11-25.
6. **Auditoria JSON em stdout → agregador.** JSON estruturado, **uma linha por evento**, sem CR/LF injetados, sem secrets/PII; o operador confirmou em 2026-11-25 o destino: agregador. Fontes: OWASP Logging Cheat Sheet — 2026-11-25; OWASP A09:2025 — 2026-11-25.

### Inferências (rótulo obrigatório)

| Afirmação | Classificação | Ação |
|---|---|---|
| Chave de delete exige `Full access` | **INFERNÊNCIA** a partir de "Full access = all read/write" + ação `traces:delete` | Confirmar com IT de 403 usando `Viewer` e `Application` na rota de delete antes de produção; se falhar, revisar emenda |
| `Full access` como única opção de delete | **INFERNÊNCIA** — a doc não define um escopo "delete-only" | Não afirmar que não exista outra opção; IT decide |
| Cron do host via Docker Compose como plataforma | **Decisão confirmada pelo operador em 2026-11-25** (não mais inferência) | Registrada como plataforma; `CronJob`/`concurrencyPolicy`/`startingDeadlineSeconds` permanecem só como alternativa descartada, não plano ativo |
| DLP externo / envio de texto para serviço de terceiro (contexto do redator) | **INFERNÊNCIA** | Fora desta fatia; ver ADR-003 |

## Decisões operacionais registradas (2026-11-25, 4 respostas do operador)

1. **Plataforma do job:** Docker Compose + cron do host (confirmado em 2026-11-25; `CronJob` k8s fica como alternativa descartada, não plano ativo).
2. **Auditoria:** stdout → agregador, **JSON por linha**; `traceIds`, `window` e `timestamp` somam-se aos seis campos congelados, com `submitted` no lugar do nome antigo de contagem.
3. **Cutoff:** `startTime` do root — determinístico e reexecutável com a mesma janela (`toStartTime` exclusivo).
4. **Campo de contagem:** `submitted` = nº de IDs submetidos; prova de exclusão só por re-leitura v2.

## Pendente (IT de 403) — escopo da chave de delete

- **Inferência `Full access`:** a doc só define "all read/write" e a ação `traces:delete`; que a terceira chave deva ser `Full access` continua **inferência**, **a confirmar por IT de 403** com `Viewer` e `Application` na rota de delete antes de produção. Nenhuma chave de delete no backend. Se o IT falhar, revisar esta emenda — não promover a inferência a contrato por palpite.

**Débitos de produção registrados no review (2026-11-25 — não bloqueiam merge):**
- `docker/check-retention-gate.sh` valida apenas cron/compose/"30"; **deve exigir a evidência do `key-scope-it.sh`** (`application_delete=403`, `viewer_delete=403`, `full_delete=200`) antes de permitir implantação — sem isso, a inferência `Full access` pode seguir não provada em produção.
- `docker/cron/langfuse-retention.cron` chama `docker compose` sem carregar o cofre; **a origem de `LANGFUSE_RETENTION_AUTH` deve ser explícita** (`.env`/cofre) no crontab, senão o job falha às 03:00 e a retenção some com o gate ainda verde.
- Melhorias não bloqueantes: ler os roots com chave `Viewer` em vez da chave de delete (menor privilégio); guardar `Instant.parse` contra `DateTimeParseException` para não perder a linha de auditoria.

### Impacto nas tasks (para o `engineer`, sem alteração aqui)

- **ACC-01** (RF-01): contrato inalterado; o job **não é usuário do proxy** — as capacidades `telemetry.read`/`telemetry.admin` continuam sendo para pessoas. Nenhuma capacidade do proxy é usada para autorizar a CLI; a execução é cron do host via Docker Compose, **fora do proxy e do backend**.
- **ACC-07** (RF-07): manter a negação de leitura com `Application`; acrescentar negação de **delete** com `Application` e com `Viewer`, e o **IT de 403** que valida/invalida a inferência `Full access`. Nenhuma chave de delete no backend.
- **ACC-11** (RF-11): a captura da query passa a exigir `fields=core,basic`, `fromStartTime` inclusivo, `toStartTime` exclusivo e paginação por `meta.cursor` até cursor ausente; **cutoff = `startTime` do root** (determinístico/reexecutável); asserção de fronteira (29/31 dias) e agrupamento por `traceId`.

## Alternativas descartadas

| Alternativa | Motivo do descarte |
|---|---|
| RBAC por projeto + Data Retention nativa (EE) | Self-hosted = Enterprise Edition nas duas tabelas de disponibilidade; contraria "Community sem EE". Fonte: RBAC e Data Retention — 2026-11-25. |
| Langfuse Cloud | Conflita com self-host (residência/controle de dado) e não resolve a decisão de job externo auditável. |
| Tratar auth no proxy como RBAC por projeto | Falsidade documentada: papéis por projeto são EE; o proxy não conhece projetos do Langfuse. Fonte: RBAC — 2026-11-25. |
| Nenhuma política de retenção | Self-host guarda eventos indefinidamente por default — viola RF6/RB4. Fonte: Data Retention — 2026-11-25. |
| Chave `Application` para leitura | Fere RF5/RB3; `Application` é escrita de traces (com fetch de prompts/skills), não leitura de traces. Fonte: RBAC — 2026-11-25. |
| Kubernetes `CronJob` | Sem evidência de k8s no repo (só `docker-compose-*.yml`); plataforma confirmada pelo operador em 2026-11-25 = Compose + cron do host, então `CronJob` fica descartado — só reabre se a plataforma mudar por nova decisão. `concurrencyPolicy: Forbid` e `startingDeadlineSeconds` são controles oficiais desse cenário hipotético, não plataforma atual. Fonte: Kubernetes CronJob — 2026-11-25. |
| GitHub Actions `schedule` | Atraso em picos, execuções podem ser descartadas, só default branch; além disso um SaaS precisaria alcançar o Langfuse interno — conflita com hardening/ADR-004. Fontes: GitHub Docs (eventos de workflow) e Langfuse hardening — 2026-11-25. |
| `@Scheduled`/Spring Batch no backend | O backend passaria a ter a chave de delete, quebrando separation-of-duties (`Application` só escreve; leitura é `Viewer`; delete é só do job). Fonte: RBAC — 2026-11-25. |
| Confiar em `{"message":…}` como confirmação de exclusão | A resposta não informa quantidade removida; exclusão é assíncrona e sem notificação. Fonte: Data Deletion — 2026-11-25. |

## Consequências

**Positivas**
- Cumpre a decisão Community sem licença EE, com separation-of-duties: operador/suporte leem, time de IA administra, backend só escreve e **nunca detém chave de delete**.
- Contrato de leitura/escrita verificado em fonte oficial (cursor, `fields`, limites, lotes) — sem assinatura de API assumida de memória.
- Auditoria padronizada em JSON estruturado em stdout reduz risco de log injection e facilita ingestão pelo coletor escolhido pelo operador.
- RF5 e RF6 têm critérios observáveis (403, negação com chave de ingestão, trace 29/31 dias, parâmetro auditável).
- Auditoria do job registra quantidade candidata/submetida (`submitted`) e erros; combinada com a re-leitura v2, detecta limpeza parcial sem confundir submissão com confirmação.

**Negativas**
- A CLI é uma nova unidade deployável (container + cron do host) com seu próprio ciclo de credencial e monitoramento; se parar, a retenção some até a trava de produção detectar.
- `submitted` superestima a exclusão real: sem re-leitura v2, uma limpeza parcial pareceria sucesso.
- Permissão de delete inferida (`Full access`) ainda depende de IT de 403 — até lá, a separação de deveres está correta no desenho, mas não provada na implantação.
- Proxy é controle grosseiro: todo usuário autorizado vê o mesmo projeto; sem isolamento por projeto no Langfuse.
- Job externo é dependedência operacional nova: se parar, a retenção some silenciosamente; por isso a trava de produção.
- Exclusão irreversível; um cutoff errado apaga traces válidos.
- Permissões de API key são imutáveis após criação — troca exige criar nova chave e revogar antiga (fonte: RBAC — 2026-11-25).

## Fontes

- Decisões operacionais do operador (2026-11-25): plataforma, destino/formato da auditoria, cutoff e nome do campo de contagem — registradas neste ADR, sem fonte externa nova.
- Langfuse — RBAC (papéis por projeto = EE; permissões de API key `Application`/`Viewer`; chaves por projeto) — https://langfuse.com/docs/administration/rbac — 2026-11-25.
- Langfuse — Data Retention (EE no self-host; default indefinido; `DELETE` por API) — https://langfuse.com/docs/administration/data-retention — 2026-11-25.
- Langfuse — hardening (TLS/VPN/zero-trust, disable signup, admin restrito) — https://langfuse.com/self-hosting/configuration/hardening — 2026-11-25.
- Langfuse — Public API (Observations API v2, filtros `isRootObservation`/`fromStartTime`/`toStartTime`) — https://langfuse.com/docs/api-and-data-platform/features/public-api — 2026-11-25.
- Langfuse — OpenAPI spec (`DELETE /api/public/traces`, `DeleteTraceResponse`) — https://cloud.langfuse.com/generated/api/openapi.yml — 2026-11-25.
- RESEARCH — eixo D4 e §7 — 2026-11-25.
- Langfuse — Observations API (v2: `core`/`basic`, `isRootObservation` em `basic`, `fromStartTime` inclusivo, `toStartTime` exclusivo, `limit` 50/1000, `meta.cursor`, root lógico assume 1 root exportado por trace) — https://langfuse.com/docs/api-and-data-platform/features/observations-api — 2026-11-25; Public API — https://langfuse.com/docs/api-and-data-platform/features/public-api — 2026-11-25.
- Langfuse — Data Deletion (`DELETE /api/public/traces` em lote, exclusão assíncrona ~15 min, sem confirmação, verificar com re-leitura) — https://langfuse.com/docs/administration/data-deletion — 2026-11-25.
- Langfuse — API key permissions (`Viewer` read-only, `Application` envia traces, `Full access` = all read/write, chaves imutáveis) — https://langfuse.com/docs/administration/rbac#api-key-permissions — 2026-11-25; changelog 2026-10-16 — https://langfuse.com/changelog/2026-10-16-project-api-key-permissions — 2026-11-25.
- Oracle — JDK 25 `java.net.http.HttpClient` — https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html — 2026-11-25.
- OWASP — Logging Cheat Sheet (JSON estruturado, sanitizar CR/LF, excluir secrets/PII) — https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html — 2026-11-25; A09:2025 Security Logging & Alerting Failures — https://owasp.org/Top10/2025/A09_2025-Security_Logging_and_Alerting_Failures — 2026-11-25.
- Kubernetes — CronJob (`concurrencyPolicy: Forbid`, `startingDeadlineSeconds`) — https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/ — 2026-11-25.
- GitHub Docs — eventos que disparam workflows (`schedule`: atraso em picos, jobs descartados, só default branch) — https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows — 2026-11-25.
- Evidência local — `docker/docker-compose-vetor.yml`, `docker/docker-compose-redis.yml`; ausência de manifest k8s no repo — 2026-11-25.
- Deepsearch do `engineer` (nota canônica no Brain; 22 fontes; Context7 indisponível) — `estudos/projetos/hive/adr-003-004-deepsearch-redacao-retencao-2026-11-25` — 2026-11-25.
