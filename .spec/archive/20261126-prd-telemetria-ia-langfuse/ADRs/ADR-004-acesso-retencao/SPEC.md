<!-- scope: adr/ADR-004 -->
# SPEC — ADR-004: Controle de acesso no reverse proxy e retenção externa de 30 dias (Community)

> **Escopo:** somente a decisão congelada no `ADR.md` deste diretório. Cada `RF`/`NFR` abaixo aponta para a cláusula do ADR que o origina. Não especifica modelo de spans/correlação (ADR-001), endpoint Basic `pk:sk`/header v4/exporter (ADR-002), redação do texto (ADR-003) nem métricas/custo em Micrometer (ADR-005).
>
> **Rastreabilidade:** `ADR.md` (decisão + Interface congelada + decisão operacional do operador) → esta SPEC (`RF-01`..`RF-17`, `NFR-01`..`NFR-06`) → `TASKS.md` (`ACC-01`..`ACC-23`) → evidência no `VERIFY.md` pós-merge. `depende-de: ADR-002, ADR-003`. Status do ADR: Proposto (sem merge).
>
> **Regra de não-invenção:** o ADR decide que a retenção é feita por **job externo auditável**; executor, plataforma e destino da auditoria estão **registrados na emenda ACC-23 e nas decisões operacionais de 2026-11-25** — esta SPEC não inventa outros. Os contratos de API (`GET /api/public/v2/observations`, `DELETE /api/public/traces`) são os congelados no ADR, com URL + data em §12 — nenhuma assinatura nova é inferida. Autorização no proxy **nunca** é descrita como "RBAC por projeto" (isso é Enterprise Edition e foi descartado).

## 1. ADRs

### ADR-004 (este ciclo)

- **Decision:** Langfuse self-host v4 **Community**, sem EE. Autorização no **reverse proxy** na frente da UI e da API: capacidades lógicas `telemetry.read` (Admin Hive, Suporte) e `telemetry.admin` (time de IA); sem capacidade ⇒ **403**. Signup e login por usuário/senha desabilitados, endpoints de administração só na rede interna do proxy, TLS obrigatório. O proxy controla o acesso à instância — **não** cria papéis por projeto; primeira fatia tem um projeto e nenhuma promessa de isolamento entre projetos. Chaves separadas: `Application` para escrita de traces (somente exporter do ADR-002), `Viewer` para leitura programática (cofre, não entregue ao backend), chave com permissão de delete para limpeza (**escopo `Full access` ainda inferência, a confirmar por IT de 403**). Retenção por **job externo auditável**, janela `retention_days=30`: seleção de observations root (`isRootObservation=true`) com `startTime < cutoff`, cutoff = **`startTime` do root** (determinístico/reexecutável), via Observations API v2 com `fields=core,basic` e paginação por `meta.cursor`; agrupamento por `traceId` e exclusão em lote por `DELETE /api/public/traces` com `{"traceIds":[…]}`. **Executor: CLI Java standalone, cron do host via Docker Compose, fora do proxy e do backend.** Auditoria estruturada em **stdout → agregador, JSON por linha**, com `run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors`, `traceIds`, `window` e `timestamp`; `submitted` conta IDs enviados, prova real por re-leitura v2. Exclusão irreversível; sem job ativo, bloquear produção. (ADR Decisão 1–5 + Interface congelada + decisões operacionais de 2026-11-25)
- **Context:** no self-host, RBAC por projeto e Data Retention nativa são **Enterprise Edition**; sem política o Langfuse guarda eventos indefinidamente. Credencial `Application` envia traces, `Viewer` é somente leitura. Edição/licença é premissa do operador; executor, plataforma e destino da auditoria foram registrados pelo operador em 2026-11-25 — não fatos de código. (ADR Contexto)
- **Trade-offs:** controle externo verificável por 403/negação e por trace 29/31 dias, sem licença EE, contra controle grosseiro (todo autorizado vê o mesmo projeto) e dependência operacional nova (job parado = retenção some silenciosamente). Irreversibilidade da exclusão exige cutoff auditável. (ADR Consequências)

ADRs afetados: nenhum. `depende-de: ADR-002` (credencial `pk:sk`/endpoint de ingestão — a chave `Application` aqui é a mesma usada pelo exporter, mas **nunca** para leitura) e `ADR-003` (trace contém somente texto redigido; a leitura autorizada vê o trace já redigido). ADR-005 consome as mesmas chaves/endpoint, mas não altera esta decisão.

## 2. Domain Context, Tenant Strategy & Provisioning

- **Bounded Context:** `observability-access-retention` — quem pode chegar à instância Langfuse e por quanto tempo os traces permanecem. Nenhuma mudança no domínio `assistant`.
- **System Type:** reverse proxy (UI + API do Langfuse) + job externo recorrente. Nenhum módulo `api`/`impl`/`public` do backend é alterado nesta fatia.
- **Tenant Provisioning:** N/A — nenhum tenant, projeto ou credencial de domínio é criado. O proxy autoriza pessoas, não projeta tenants do Langfuse.
- **Tenant Lifecycle:** N/A — nenhum ciclo de vida de tenant/RBAC da aplicação é tocado (PRD RB2/RB7).
- **Projeto único:** a primeira fatia opera **um** projeto Langfuse. Qualquer necessidade futura de isolamento entre projetos reabre licença/edição (EE) e exige novo ADR — não é extensão silenciosa deste controle. (ADR Decisão 2)

## 3. Identity & Access Management (IAM)

- **IdP / Protocol:** inalterado no backend. Autorização de leitura/administração ocorre **antes** da UI/API do Langfuse, no proxy, por capacidade lógica declarada — não por papel de projeto do Langfuse.
- **RBAC Matrix (capacidades do proxy):**

| Role | Resource / Domain | Permissions (C, R, U, D, Execute) | Scoped to Tenant/Project? |
|------|-------------------|-----------------------------------|---------------------------|
| Admin Hive | UI/API do Langfuse via proxy | R (`telemetry.read`) | Não |
| Suporte | UI/API do Langfuse via proxy | R (`telemetry.read`) | Não |
| Time de IA | UI/API + administração via proxy | C/R/U/D/Execute (`telemetry.admin`) | Não |
| Sem capacidade | qualquer rota da UI/API | nenhuma ⇒ **403** | Não |
| `telemetry.read` | rotas de administração | negado (sem `telemetry.admin`) | Não |
| Exporter (ADR-002) | `POST /api/public/otel/v1/traces` | escrita de traces via chave `Application` | Não |
| Leitura programática | Observations API v2 / traces | R via chave `Viewer` (cofre, não backend) | Não |

- **MFA:** N/A — fora de escopo (PRD §2); o proxy é o ponto de imposição das capacidades.
- **Sessão/token:** nenhum token de chat é gerado ou alterado. `turnId`/`clientId` continuam como propagados pelo ADR-001; esta fatia não os usa como chave de autorização.
- **Hardening:** signup e login por usuário/senha desabilitados; endpoints de administração restritos à rede interna do proxy; TLS obrigatório para UI e API. (ADR Decisão 1)
- **Chaves:** (a) `Application` — somente envio de traces, usada pelo exporter do ADR-002, **nunca** para leitura; (b) `Viewer` — somente leitura, guardada em cofre e não entregue ao backend; (c) chave com permissão de delete — usada somente pelo job de limpeza. Permissões de API key são imutáveis após criação; troca exige criar nova e revogar antiga. Nenhuma credencial em log, trace, payload ou resposta (RB3).

## 4. Security, Key Management & Disaster Recovery

- **Key Management:** cofre do operador para `Viewer` e para a chave de delete; `pk:sk` de ingestão permanece no cofre do ADR-002. Nenhuma chave é nomeada em código, config versionada, log, trace, payload ou resposta. Escolha do cofre é premissa operacional — não decidida aqui.
- **Key Lifecycle & Segregation:** finalidades separadas (escrita ≠ leitura ≠ exclusão); rotação/troca por revogação da chave antiga e criação de nova (ADR Consequência negativa). Nenhuma chave é reutilizada entre finalidades.
- **Transporte:** TLS obrigatório na frente da UI/API; acesso administrativo só pela rede interna do proxy.
- **Auditoria:** stdout → agregador, **JSON por linha**, com `run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors`, `traceIds` submetidos, janela (`window`) e `timestamp`; sem secrets/PII e sem CR/LF injetados. Destino = agregador (decisão do operador 2026-11-25).
- **BDR:** N/A — nenhum backup/PITR/RTO/RPO novo é definido; a exclusão de traces é **irreversível** e não há restore na primeira fatia. Cutoff errado apaga traces válidos; por isso a janela é auditável.
- **LGPD:** classificação/base legal seguem premissa do operador (PRD §6); a mitigação técnica aqui é acesso controlado + janela curta + auditoria.

## 5. Data Model & Concurrency

- Nenhuma entidade, tabela, migration Flyway/Liquibase, chave Redis ou dado de domínio é criado ou alterado. Traces e observations vivem no Langfuse (PRD §11).
- **ERD:** N/A — sem entidade nova neste ciclo.
- **Migrations:** N/A — sem migration Java neste ciclo.
- **Concorrência:** o job recorrente deve ser seguro a reexecução: uma segunda execução com o mesmo cutoff não pode duplicar exclusão nem corromper a auditoria; `run_id` identifica cada execução. A seleção é por janela temporal, não por estado mutável do backend. Lock + cutoff determinístico por `startTime` do root estão registrados na emenda ACC-23 (2026-11-25); o job roda em cron do host via Docker Compose, fora do proxy e do backend.

## 6. Event Model, Integrations & Messaging Isolation

- **Event Registry:** N/A — nenhum evento de domínio novo. O log de auditoria é log estruturado do job, não evento de domínio com produtor/consumidor no broker.
- **Integração de entrada:** Observations API v2 (`GET /api/public/v2/observations?isRootObservation=true&fromStartTime=<anterior>&toStartTime=<cutoff>&fields=core,basic`) para selecionar roots expirados, paginando por `meta.cursor` até cursor ausente (`limit` ≤ 1000). `fields=core,basic` é obrigatório porque `isRootObservation` está em `basic`; `fromStartTime` é limite inferior inclusivo opcional; `toStartTime=<cutoff>` é o limite superior exclusivo obrigatório, com **cutoff = `startTime` do root** (determinístico/reexecutável, 2026-11-25).
- **Integração de saída:** `DELETE /api/public/traces`, corpo `{"traceIds":[…]}`, resposta `{"message":…}`, com chave de delete. Agrupamento por `traceId` antes do envio.
- **Isolamento:** nenhum trace de um projeto/turno é usado como critério de autorização; o proxy não conhece projetos do Langfuse. Nenhuma credencial cruza de uma finalidade para outra.

## 7. Invariants & Property-Based Testing

### 7.1 Invariantes

| ID | Propriedade | Verificação |
|---|---|---|
| INV-1 | Capacidades são exatamente `telemetry.read` e `telemetry.admin`; grafia/prefixo imutáveis | asserção sobre config do proxy + teste de negação |
| INV-2 | Nenhuma capacidade ⇒ 403 na UI e na API; `telemetry.read` nunca executa ação de admin | `curl` com/sem capacidade; status HTTP observado |
| INV-3 | Credencial `Application` nunca lê trace nem deleta; leitura programática usa somente `Viewer`; `Application` e `Viewer` recebem **403** na rota de delete (IT) | tentativa de GET/DELETE com chave de ingestão negada; IT de 403 com `Application` e `Viewer` |
| INV-4 | "auth no proxy" nunca é chamado de "RBAC por projeto" em doc/config/código | varredura estática do termo proibido |
| INV-5 | `retention_days=30` é o único parâmetro de janela auditado | asserção no log de auditoria e na config do job |
| INV-6 | Seleção usa `isRootObservation=true`, `fields=core,basic`, paginação por `meta.cursor` e `startTime < cutoff`, com cutoff = `startTime` do root | captura da query + asserção sobre `toStartTime=<cutoff>` e presença de `fields`/cursor |
| INV-7 | Exclusão é em lote, por `DELETE /api/public/traces` com `{"traceIds":[…]}`, agrupada por `traceId` | captura da requisição + resposta `{"message":…}` |
| INV-8 | Toda execução registra `run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors`, `traceIds`, `window` e `timestamp`, em JSON por linha no stdout | validação estrutural do log (schema/campo presente) |
| INV-9 | Trace com 29 dias presente; trace com 31 dias ausente após o job | seed temporal + consulta Observations API v2 |
| INV-10 | Nenhuma credencial aparece em log, trace, payload ou resposta | varredura de superfícies + negação de leitura |
| INV-11 | dono = time de IA, cadência = diário às 03:00; destino do log configurado na implantação sem invenção | registro documental + asserção de não-invenção |
| INV-12 | Sem job ativo, implantação em produção é bloqueada | gate de deploy observável (PRD §6) |
| INV-13 | Job roda **fora do proxy e do backend** (cron do host via Docker Compose); nenhuma capacidade `telemetry.*` autoriza a CLI | inspeção da config de invocação + varredura de capacidades no job |

### 7.2 Invariantes de workflow

- **Acesso:** usuário com `telemetry.read`/`telemetry.admin` passa no proxy; sem capacidade ⇒ 403; `telemetry.read` tentando administração ⇒ 403; exporter com `Application` escreve, nunca lê.
- **Retenção:** seleção de roots expirados → agrupamento por `traceId` → `DELETE` em lote → log estruturado da execução. Falha parcial é registrada em `errors`, não silenciada.
- **Janela:** `startTime >= cutoff` permanece; `startTime < cutoff` é excluído. Exclusão não tem volta (sem restore).
- **Produção:** sem job ativo, não há implantação em produção — trava explícita, não default implícito.

### 7.3 Restrições de geração de dados de teste

| Campo | Tipo | Restrição | Objetivo |
|---|---|---|---|
| `capability` | enum | `telemetry.read`, `telemetry.admin`, `NONE` | provar INV-1/INV-2 sem papel invented |
| `keyPurpose` | enum | `APPLICATION`, `VIEWER`, `DELETE` | provar separação de finalidade (INV-3) |
| `traceAgeDays` | inteiro | `29` (presente) e `31` (ausente); borda `30` | provar RF-14/INV-9 |
| `isRootObservation` | booleano | `true` na seleção; `false` nunca selecionado | provar INV-6 |
| `cutoff` | timestamp | `startTime` do root; `now - 30d`, determinístico/reexecutável; `toStartTime=<cutoff>` | janela auditável |
| `auditRecord` | objeto | contém 6 campos congelados + `traceIds`, janela, timestamp | provar INV-8 |

## 8. Requisitos Funcionais

- **RF-01 — Negação no proxy.** Usuário sem `telemetry.read` nem `telemetry.admin` recebe **403** na UI e na API do Langfuse. O job externo **não é usuário do proxy**: roda por cron do host via Docker Compose, fora do proxy e do backend, sem capacidade `telemetry.*`. *(ADR Decisão 1 + Interface "Negação de acesso"/"Executor do job"; RF5 do PRD.)*
- **RF-02 — Capacidades exatas.** O proxy reconhece somente `telemetry.read` e `telemetry.admin`, com prefixo e grafia congelados; nenhum sinônimo é aceito. *(ADR Interface "Capacidades do proxy".)*
- **RF-03 — Leitura autorizada.** Admin Hive e Suporte, com `telemetry.read`, leem o trace de teste pela UI/API; `telemetry.read` não concede administração. *(ADR Decisão 1 + Contexto; RF5.)*
- **RF-04 — Administração restrita.** Somente o time de IA, com `telemetry.admin`, executa ações de administração; tentativa com `telemetry.read` ⇒ 403. *(ADR Decisão 1 + Contexto.)*
- **RF-05 — Hardening do proxy.** Signup e login por usuário/senha desabilitados; endpoints de administração acessíveis somente pela rede interna do proxy. *(ADR Decisão 1; hardening — fonte em §12.)*
- **RF-06 — TLS obrigatório.** Toda chamada à UI/API do Langfuse passa por TLS; HTTP simples é recusado. *(ADR Decisão 1.)*
- **RF-07 — Chave de ingestão não lê nem deleta.** `Application` serve somente ao envio de traces; tentativa de leitura com a credencial de ingestão é negada — nunca retorna trace. **IT de 403** na rota de delete com `Application` **e** com `Viewer` valida/invalida a inferência de que a terceira chave exige `Full access`; nenhuma chave de delete no backend. *(ADR Decisão 3 + Interface "Escopo da chave de ingestão"/"Chave de delete"; RF5/RB3.)*
- **RF-08 — Chave de leitura separada.** `Viewer` é usada para leitura programática, guardada em cofre e não entregue ao backend; é distinta da chave de ingestão e da chave de delete. *(ADR Decisão 3 + Interface "Escopo da chave de leitura".)*
- **RF-09 — Nenhuma credencial em superfície observável.** Nenhuma chave aparece em log, trace, payload ou resposta, inclusive em mensagem de erro de negação. *(ADR Decisão 3; RB3; PRD §9.)*
- **RF-10 — Proxy não é RBAC por projeto.** O controle é de acesso à instância, sem papéis por projeto; primeira fatia tem um projeto e nenhuma promessa de isolamento. Nenhum artefato descreve o proxy como "RBAC por projeto". *(ADR Decisão 2 + Interface "Equivalência proibida"; alternativa EE descartada.)*
- **RF-11 — Seleção de roots expirados.** O job consulta `GET /api/public/v2/observations?isRootObservation=true&fromStartTime=<anterior>&toStartTime=<cutoff>&fields=core,basic`, paginando por `meta.cursor` até cursor ausente (`limit` ≤ 1000); **cutoff = `startTime` do root** (`now - retention_days`, determinístico/reexecutável, `toStartTime` exclusivo); só observations com `startTime < cutoff` entram na limpeza. *(ADR Decisão 4 + Blueprint de contrato + decisão operacional 2026-11-25; Observations API v2 — §12.)*
- **RF-12 — Exclusão em lote.** Agrupar por `traceId` e enviar `DELETE /api/public/traces` com `{"traceIds":[…]}`, usando chave com permissão de delete; resposta `{"message":…}`. *(ADR Decisão 4 + Blueprint/Interface "Endpoint de exclusão"; OpenAPI — §12.)*
- **RF-13 — Janela auditável.** `retention_days=30` é parâmetro auditável da execução, registrado na config do job e no log — não valor implícito. *(ADR Decisão 4 + Interface "Janela de retenção".)*
- **RF-14 — Fronteira 29/31 dias.** Após o job, trace com 29 dias permanece consultável e trace com 31 dias não é encontrado. *(ADR Decisão 5; RF6 do PRD.)*
- **RF-15 — Auditoria estruturada.** Cada execução registra, em **JSON por linha no stdout coletado por agregador**, `run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors`, `traceIds` submetidos, `window` e `timestamp`. `submitted` conta IDs enviados; prova de exclusão exige re-leitura v2. *(ADR Interface "Campos de auditoria"/"Formato de auditoria" + decisões operacionais 2026-11-25.)*
- **RF-16 — Trava de produção.** Sem job ativo/cadência verificável, a implantação em produção é bloqueada; ausência de retenção não é default aceito. *(ADR Decisão 4; PRD §6/RB4.)*
- **RF-17 — Irreversibilidade.** Não há mecanismo de restore/soft-delete que reintroduza traces excluídos pelo job na primeira fatia. *(ADR Decisão 5 + Consequência negativa.)*

## 9. Requisitos Não-Funcionais

- **NFR-01 — Nomenclatura congelada.** Capacidades, escopo das chaves (`Application`/`Viewer`), `retention_days=30`, endpoints de leitura/exclusão, `fields=core,basic`, `meta.cursor`, cutoff = `startTime` do root e os campos de auditoria (`run_id`, `executed_at`, `retention_days`, `candidates`, `submitted`, `errors`, `traceIds`, `window`, `timestamp`) são imutáveis neste e nos ADRs downstream; a equivalência "proxy = RBAC por projeto" é proibida. *(ADR Interface congelada + decisões operacionais 2026-11-25.)*
- **NFR-02 — Community sem EE.** Zero uso de RBAC por projeto, Data Retention nativa, masking EE ou qualquer recurso de Enterprise Edition; nenhuma licença EE é adicionada. *(ADR Decisão 2 + Alternativas descartadas.)*
- **NFR-03 — Premissas operacionais registradas.** dono = **time de IA**, cadência = **diário às 03:00**, plataforma = **Docker Compose + cron do host**, auditoria = **stdout → agregador, JSON por linha** com `traceIds`/`window`/`timestamp`, cutoff = `startTime` do root, campo de contagem = `submitted`. Nenhum destino, scheduler ou implementação de job é inventado nesta SPEC. *(ADR Decisão operacional do operador, 2026-11-25.)*
- **NFR-04 — Rastreabilidade e fontes.** Cada `RF`/`NFR` desta SPEC vira exatamente 1 task em `TASKS.md`; toda afirmação sobre API de terceiro cita URL + data; nenhuma assinatura de API é aceita de memória. *(ADR Fontes; protocolo deepsearch.)*
- **NFR-05 — Não regressão e segurança.** Suíte completa 0 falhas; 0 credencial em log/trace/payload/resposta; contrato do chat, cota, `turnId`/`clientId` e headers inalterados; frontend N/A (a UI é produto Langfuse atrás do proxy, não app nova). *(PRD RB2/RB3/§9; H1/H2/H6.)*
- **NFR-06 — Job externo sem implementação inventada.** A decisão de existir job externo auditável é do ADR; executor (CLI Java standalone), plataforma (cron do host via Docker Compose) e destino (agregador via stdout) estão registrados na emenda ACC-23 e nas decisões operacionais de 2026-11-25. Esta SPEC não acrescenta scheduler, linguagem, framework, storage ou destino além desses. Permanece como **inferência e IT pendente** o escopo da chave de delete (`Full access` a confirmar por IT de 403 com `Viewer`/`Application`). *(Emenda ACC-23; ADR Decisão 4; regra de não-invenção.)*

## 10. Critérios de Aceite (executáveis)

Comando canônico de suíte: `npm test` (`harness.config.json.commands.suiteFull`). Verificações de integração: `mvn -f backend/pom.xml -Pintegration verify`. Variáveis de ambiente de teste (fornecidas pelo operador, **não** definidas aqui): `$PROXY` (base URL do proxy), `$LANGFUSE` (base URL da API), `$INGESTION_KEY`, `$VIEWER_KEY`, `$DELETE_KEY`, `$CUTOFF` (`now - 30d`), `$AUDIT_LOG`, `$JOB_RUNNER` (invocação do job — assinatura fornecida na implantação, não especificada nesta SPEC). `T31` = trace semeado com 31 dias; `T29` = trace semeado com 29 dias.

**Estratégia de testes:** (1) contrato do proxy — `curl` com cada capacidade e sem capacidade, verificar status HTTP e negação de admin; (2) integração de chaves — tentativa de leitura com `Application`, leitura com `Viewer`, exclusão com chave de delete; (3) integração de retenção — seed temporal `T31`/`T29`, execução do job, consulta Observations API v2 antes/depois; (4) estáticos — ausência de termo proibido, de EE e de credencial; (5) documental — gate de decisão do job e premissas operacionais. Toda task em `TASKS.md` escreve o teste antes da implementação.

| Req | Task | Comando / verificação | Asserção observável |
|---|---|---|---|
| RF-01 | ACC-01 | `curl -sS -o /dev/null -w '%{http_code}' "$PROXY/api/public/v2/observations"` e o mesmo na UI + inspeção da invocação do job | `403` em ambas; nenhum corpo com trace; job fora do proxy/backend, sem capacidade `telemetry.*` |
| RF-02 | ACC-02 | `grep -RIn 'telemetry.read\|telemetry.admin' <config do proxy>` + teste com capacidade inventada | somente as 2 capacidades exatas; capacidade inventada ⇒ 403 |
| RF-03 | ACC-03 | `curl -sS -H "Authorization: Bearer $READ_TOKEN" "$PROXY/api/public/v2/observations?isRootObservation=true"` | 200 com trace de teste; mesmo token em rota admin ⇒ 403 |
| RF-04 | ACC-04 | `curl -X POST ...` em rota de administração com `$READ_TOKEN` e com `$ADMIN_TOKEN` | 403 com read; 2xx com admin |
| RF-05 | ACC-05 | `curl` em signup/login + rota admin de rede externa | signup/login desabilitados; admin fora da rede interna ⇒ 403 |
| RF-06 | ACC-06 | `curl -sS -o /dev/null -w '%{http_code}' "http://$PROXY/..."` | HTTP simples recusado; TLS obrigatório |
| RF-07 | ACC-07 | `curl -sS -H "Authorization: Basic $INGESTION_KEY" "$LANGFUSE/api/public/v2/observations?isRootObservation=true"` + IT de delete com `$INGESTION_KEY` e `$VIEWER_KEY` | GET negado (0 observation, 0 traceId); **403 no DELETE com `Application` e com `Viewer`**; 0 chave de delete no backend |
| RF-08 | ACC-08 | `curl -sS -H "Authorization: Basic $VIEWER_KEY" "$LANGFUSE/api/public/v2/observations?isRootObservation=true"` + varredura do backend | leitura 200; `Viewer` em cofre; 0 `Viewer` no backend |
| RF-09 | ACC-09 | `grep -RInE "$INGESTION_KEY\|$VIEWER_KEY\|$DELETE_KEY" $AUDIT_LOG $PAYLOAD $LOG` + corpo de erro 403 | 0 ocorrência em todas as superfícies |
| RF-10 | ACC-10 | `grep -RInE 'auth no proxy.*RBAC por projeto\|RBAC por projeto.*(implementad\|habilitad\|ativad)' .spec/prd/telemetria-ia-langfuse/ADRs/ADR-004-acesso-retencao/` + inspeção do proxy | 0 uso afirmativo (termo permitido só em contexto de proibição/descarte); 0 papel por projeto; 1 projeto |
| RF-11 | ACC-11 | capturar query do job + `GET .../observations?isRootObservation=true&fields=core,basic&toStartTime=$CUTOFF` e páginas seguintes por `meta.cursor` | `isRootObservation=true`, `fields=core,basic`, cursor até ausente, `toStartTime=$CUTOFF` com cutoff = `startTime` do root; 0 root com `startTime >= cutoff` selecionado |
| RF-12 | ACC-12 | capturar requisição de exclusão do job | `DELETE /api/public/traces`, corpo `{"traceIds":[…]}` agrupado, resposta com `message` |
| RF-13 | ACC-13 | `grep -RIn 'retention_days' <config do job>` + `$AUDIT_LOG` | valor `30` em config e no log; 0 janela implícita |
| RF-14 | ACC-14 | seed `T31`/`T29` → `$JOB_RUNNER` → `GET .../observations?isRootObservation=true&fromStartTime=<28d>&toStartTime=<30d>` e `<30d..32d>` | `T29` presente; `T31` ausente (0 resultado) |
| RF-15 | ACC-15 | `jq -e '.run_id and .executed_at and .retention_days==30 and (.candidates|type=="number") and (.submitted|type=="number") and (.errors|type=="array") and (.traceIds|type=="array") and .window and .timestamp' $AUDIT_LOG` + 1 linha JSON por evento | todos os campos presentes; `submitted` = nº de `traceIds` enviados; agregador consome stdout |
| RF-16 | ACC-16 | gate de deploy com job desativado | implantação em produção bloqueada; 0 deploy sem job ativo |
| RF-17 | ACC-17 | após exclusão, tentar restorative/soft-delete e consultar `T31` | 0 mecanismo de restore; `T31` permanece ausente |
| NFR-01 | ACC-18 | `mvn -f backend/pom.xml -Pintegration verify -Dtest=FrozenAccessRetentionNamingIT` | nomes/endpoints/campos exatamente como na Interface congelada; 0 rename |
| NFR-02 | ACC-19 | `grep -RIn 'enterprise\|EE\|masking\|project.role' <config do proxy> <config do job>` | 0 ocorrência de recurso/licença EE |
| NFR-03 | ACC-20 | auditoria documental de `ADR.md` §"Decisões operacionais registradas" + config de cadência | dono time de IA; cadência diária 03:00; Compose + cron do host; stdout → agregador; cutoff `startTime`; `submitted` — 0 destino inventado |
| NFR-04 | ACC-21 | `grep -c '^### ACC-' TASKS.md` + auditoria de URLs | 23 seções ↔ 17 RF + 6 NFR; 0 órfão; toda URL de terceiro com data |
| NFR-05 | ACC-22 | `npm test` + `mvn -f backend/pom.xml -Pintegration verify` + `git diff --name-only` | 0 falhas; 0 credencial; 0 diff em contrato/cota/RBAC do chat; frontend N/A |
| NFR-06 | ACC-23 | auditoria documental da emenda ACC-23 e de §"Decisões operacionais registradas" no `ADR.md` | emenda registrada com URL + data (CLI Java, cron do host/Compose, agregador); 4 decisões do operador com data 2026-11-25; IT 403 da chave de delete segue pendente como inferência |

## 11. Riscos

| Risco | Mitigação | Origem no ADR |
|---|---|---|
| Job parado ⇒ retenção some silenciosamente | trava de produção (RF-16) + auditoria por execução (RF-15) | Consequência negativa |
| Cutoff errado apaga traces válidos | janela auditável (RF-13) + fronteira 29/31 testada (RF-14) | Consequência negativa |
| Proxy tratado como RBAC por projeto (falsidade/EE) | RF-10 + NFR-02 + varredura do termo proibido | Alternativa descartada |
| Chave de ingestão usada para leitura | RF-07 + separação de finalidades (RF-08) | Consequência/RB3 |
| Credencial vaza em erro/auditoria | RF-09 (varredura de superfícies) | Decisão 3 |
| Exclusão parcial não detectada | `candidates`/`submitted`/`errors` no log + re-leitura v2 (RF-15) | Consequência positiva |
| Implementação de job inventada sem fonte | emenda ACC-23 registrada com URL + data (2026-11-25) | Protocolo deepsearch |
| Escopo da chave de delete inferido (`Full access`) | IT de 403 com `Application`/`Viewer` antes de produção (RF-07/ACC-07) | Emenda ACC-23 — inferência pendente |
| Troca de permissão de API key (imutável) | revogar antiga + criar nova, registrado como premissa (RF-08) | Consequência negativa |

**Fora de escopo desta SPEC (decisões de outros ADRs/operador):** contrato de spans/correlação (ADR-001); endpoint OTLP, Basic `pk:sk`, header v4 e exporter (ADR-002); redação de PII/segredos no texto (ADR-003); definição/registro de métricas e custo (ADR-005); classificação LGPD/base legal, edição/licença e implementação do job (operador/`architect`).

## 12. Fontes, decisões operacionais e pendência de IT

Copiadas do `ADR.md` (consultas em 2026-11-25), sem inclusão de fonte nova nesta SPEC:

- Langfuse — RBAC (papéis por projeto = EE; permissões de API key `Application`/`Viewer`) — https://langfuse.com/docs/administration/rbac — 2026-11-25.
- Langfuse — Data Retention (EE no self-host; default indefinido; `DELETE` por API) — https://langfuse.com/docs/administration/data-retention — 2026-11-25.
- Langfuse — hardening (TLS/VPN/zero-trust, disable signup, admin restrito) — https://langfuse.com/self-hosting/configuration/hardening — 2026-11-25.
- Langfuse — Public API (Observations API v2, filtros `isRootObservation`/`fromStartTime`/`toStartTime`) — https://langfuse.com/docs/api-and-data-platform/features/public-api — 2026-11-25.
- Langfuse — OpenAPI spec (`DELETE /api/public/traces`, `DeleteTraceResponse`) — https://cloud.langfuse.com/generated/api/openapi.yml — 2026-11-25.
- RESEARCH — eixo D4 e §7 — 2026-11-25.

**Decisões operacionais do operador (2026-11-25, registradas no ADR, não inferidas aqui):** (1) plataforma = Docker Compose + cron do host; (2) auditoria = stdout → agregador, JSON por linha, com `traceIds`/`window`/`timestamp`; (3) cutoff = `startTime` do root, determinístico e reexecutável; (4) campo de contagem = `submitted`, prova real por re-leitura v2. Somam-se a dono = time de IA e cadência = diário às 03:00. Nenhum outro destino, scheduler, linguagem ou CLI de job é escolhido por esta SPEC.

**Decisão registrada (emenda ACC-23, 2026-11-25):** executor = CLI Java standalone, `HttpClient` (JDK) + Jackson, containerizada, cron do host via Docker Compose, fora do backend e do proxy. **Pendente (IT):** o escopo da chave de delete (`Full access`) é **inferência** a confirmar por IT de 403 com `Viewer` e `Application` na rota de delete antes de produção; até lá não é contrato. `ACC-07`/`ACC-23` registram essa evidência.

**Delta Specs:** nenhum. ADR-004 não altera doc vivo (`.agents/rules/**`, `AGENTS.md`) — PRD RB7.

## 13. Score do portão (spec-gate)

Itens das seções 1–4 de `.agents/skills/sdd-compiler/asserts/spec-gate.md` (11 itens):

| # | Item | Resultado |
|---|---|---|
| 1 | ADRs (decisão + porquê) | ✅ §1 |
| 2 | DDD Modular (api/impl/public) | N/A — nenhum módulo da aplicação alterado; superfície é proxy + job externo |
| 3 | Segurança/IAM + capacidades | ✅ §3 (matriz RBAC + 403) |
| 4 | ERD | N/A — sem entidade nova neste ciclo |
| 5 | Event Registry | N/A — nenhum evento de domínio novo; auditoria é log estruturado |
| 6 | Migrations | N/A — sem migration Java neste ciclo |
| 7 | Invariantes de entidade | ✅ §7.1 (INV-1..INV-12 sobre capacidades, chaves, trace e auditoria) |
| 8 | Invariantes de workflow | ✅ §7.2 |
| 9 | Test Strategy | ✅ §10 (contrato, integração, estático, documental) |
| 10 | Single File | ✅ arquivo único |
| 11 | Domínio claro | ✅ `observability-access-retention` |

`itens aplicáveis = 7` (4 N/A com motivo); `atendidos = 7` ⇒ **score = 100%**. Gaps `🔴`/`🟠`: nenhum. `frontend_evidence`: N/A — nenhuma UI nova da aplicação (UI Langfuse é produto terceiro atrás do proxy).
