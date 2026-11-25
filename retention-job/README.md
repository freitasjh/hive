# Job de retencao — ADR-004 / ACC-23

CLI Java standalone (`java.net.http.HttpClient` do JDK + Jackson), **fora do backend e do proxy**.
Rodada por cron do host via Docker Compose (`docker/docker-compose-retention.yml`, `docker/cron/`).

## Variaveis de ambiente (cofre/secret do host; nunca literais no repo)

| Nome | Uso |
|---|---|
| `LANGFUSE_BASE_URL` | base URL da API publica do Langfuse self-host |
| `LANGFUSE_RETENTION_AUTH` | valor do header Basic da chave exclusiva de delete |
| `RETENTION_DAYS` | janela auditavel (30 no Compose) |
| `RETENTION_LOCK_FILE` | lock contra execucoes concorrentes |
| `RUN_ID` | identificador da execucao na auditoria |

## Contrato exercitado

- `GET /api/public/v2/observations?isRootObservation=true&fields=core,basic&fromStartTime=<epoch>&toStartTime=<cutoff>&limit=1000`, paginando por `meta.cursor` mesmo com `data` vazio.
- cutoff = `now` truncado a meia-noite UTC menos `RETENTION_DAYS`; `toStartTime` exclusivo.
- agrupamento por `traceId`; `DELETE /api/public/traces` com `{"traceIds":[…]}` em lotes de ate 1000.
- stdout: 1 linha JSON por execucao com `run_id`, `executed_at`, `retention_days`, `candidates`,
  `submitted`, `errors`, `traceIds`, `window`, `timestamp`. `submitted` conta IDs **enviados**;
  a exclusao so e provada por re-leitura v2 (a API nao confirma removidos).

## Testes

`mvn -f retention-job/pom.xml test` (ou `npm test` na raiz). Os testes usam servidores HTTP falsos:
nao substituem o IT real de 403 da chave de delete — esse IT esta em `src/it/key-scope-it.sh` e
exige ambiente do operador; ate la, a inferencia `Full access` segue pendente no ADR.
