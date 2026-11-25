# Proxy de acesso — ADR-004 (ACC-01..06)

`langfuse-proxy.conf.template` vai na frente da UI e da API do Langfuse self-host **Community**.
Capacidades exatas: `telemetry.read` (Admin Hive, Suporte) e `telemetry.admin` (time de IA).
`telemetry.admin` tambem le (matriz RBAC do ADR); `telemetry.read` nunca administra.
Qualquer requisicao sem capacidade recebe **403**. O controle e de acesso a instancia — **nao** cria
papéis por projeto (isso exigiria outra edicao/licenca e foi descartado no ADR).

## Substituicoes do template

| Placeholder | Origem |
|---|---|
| `__HTTP_PORT__` / `__HTTPS_PORT__` | listener do proxy; HTTP simples responde 444 |
| `__AUTH_PORT__` | gateway de capacidade (IdP) — deve validar o token e responder 200 ou 403 |
| `__UPSTREAM_PORT__` | container web do Langfuse, somente na rede interna |
| `__ADMIN_CIDR__` | CIDR interno autorizado a alcançar `/api/admin/` |
| `__CERT__` / `__KEY__` | certificado TLS do dominio do proxy, fora do repositorio |
| `__PID__` / `__ERROR_LOG__` / `__ACCESS_LOG__` | caminhos locais de runtime do nginx |

## Hardening do Langfuse (variaveis de ambiente do container, fora do repo)

- `AUTH_DISABLE_SIGNUP=true`
- `AUTH_DISABLE_USERNAME_PASSWORD=true`
- `ADMIN_API_KEY` — chave de administracao da API, restrita a rede interna; nunca exposta ao navegador
- `TELEMETRY_ENABLED=false` (opcional, política do operador)

O gateway de capacidade recebe `?capability=telemetry.read` ou `?capability=telemetry.admin` do
subrequest do nginx e deve devolver 200 somente quando o token do solicitante tiver a capacidade
exata; caso contrario, 403. Nenhuma credencial e gravada no log do proxy.

## Operacao

- dono: **time de IA**; cadencia do job de retencao: **diario as 03:00** via cron do host +
  Docker Compose (ver `docker/cron/langfuse-retention.cron`).
- auditoria: **stdout → agregador**, JSON por linha, com `submitted` (IDs submetidos ao DELETE,
  nao exclusoes confirmadas); prova real de exclusao exige re-leitura pela Observations API v2.
- O job roda **fora** do proxy e do backend; nenhuma capacidade `telemetry.*` autoriza a CLI.
