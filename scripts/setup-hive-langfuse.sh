#!/usr/bin/env bash
# Setup idempotente e fail-closed do backend Hive -> Langfuse local.
# brain-first: ferramenta brain indisponivel (namespace removido); nao inventado.
# Contrato lido de LangfuseOtlpExporterConfig.java e key-scope-it.sh, nao de memoria.
set -euo pipefail

ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
ENV_FILE="${HIVE_LF_ENV_FILE:-$ROOT/docker/.env.hive-langfuse}"
BASE_URL="${HIVE_LF_BASE_URL:-http://localhost:3000}"
TRACES_PATH='/api/public/otel/v1/traces'
HEALTH_PATH='/api/public/health'
FORCE=0
RUN_IT=0

fail() { printf 'setup: ERRO: %s\n' "$1" >&2; exit 1; }

usage() {
  cat <<'USO'
Uso: scripts/setup-hive-langfuse.sh [--force] [--it] [--help]

Configura o backend Hive para o Langfuse local. Cria um arquivo de env com:
  HIVE_TELEMETRY_ENABLED=true
  LANGFUSE_OTLP_TRACES_ENDPOINT=<base>/api/public/otel/v1/traces
  LANGFUSE_INGESTION_PK / LANGFUSE_INGESTION_SK

Variaveis do operador (valores nunca imprimidos):
  HIVE_LF_PK            public key da chave Application (pk-lf-...)
  HIVE_LF_SK            secret key da chave Application (sk-lf-...)
  HIVE_LF_ENDPOINT      opcional; default <base>/api/public/otel/v1/traces
  HIVE_LF_BASE_URL      opcional; default http://localhost:3000
  HIVE_LF_ENV_FILE      opcional; default docker/.env.hive-langfuse
  HIVE_LF_VIEWER_AUTH   so com --it: token base64 da chave Viewer (sem prefixo de esquema)
  HIVE_LF_FULL_AUTH     so com --it: token base64 da chave Full access (sem prefixo de esquema)

Opcoes:
  --force  substitui o env de destino se o conteudo divergir
  --it     apos configurar, dispara retention-job/src/it/key-scope-it.sh.
           APPLICATION_AUTH e derivado de HIVE_LF_PK/HIVE_LF_SK; CUTOFF = agora - 30d.
           ATENCAO: o IT faz DELETE real em /api/public/traces com Full access.
  --help   mostra esta ajuda

Nao cria chaves no Langfuse: operador cria org/projeto/chaves na UI.
Nunca sobrescreve docker/.env.langfuse nem docker/langfuse.env (stack do Langfuse).
USO
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --force) FORCE=1 ;;
    --it) RUN_IT=1 ;;
    *) fail 'argumento desconhecido (use --help)' ;;
  esac
  shift
done

case "${ENV_FILE##*/}" in
  .env.langfuse|langfuse.env) fail 'destino reservado ao stack Langfuse; use outro HIVE_LF_ENV_FILE' ;;
esac

[ -n "${HIVE_LF_PK:-}" ] || fail 'HIVE_LF_PK ausente'
[ -n "${HIVE_LF_SK:-}" ] || fail 'HIVE_LF_SK ausente'
for v in "$HIVE_LF_PK" "$HIVE_LF_SK"; do
  case "$v" in
    *$'\n'*|*$'\r'*) fail 'credencial com quebra de linha' ;;
  esac
done

# Espelha LangfuseOtlpExporterConfig.isValidEndpoint: path congelado, sem
# query/fragmento/userinfo, https em qualquer host e http so em localhost.
validate_endpoint() {
  local e="$1" scheme rest authority path port=''
  case "$e" in
    *\?*|*\#*|*[[:space:]]*) return 1 ;;
    *://*) ;;
    *) return 1 ;;
  esac
  scheme="${e%%://*}"
  rest="${e#*://}"
  authority="${rest%%/*}"
  [ -n "$authority" ] || return 1
  case "$authority" in
    *@*) return 1 ;;
  esac
  path="/${rest#*/}"
  [ "$path" = "$TRACES_PATH" ] || return 1
  case "$scheme" in
    https) ;;
    http)
      case "$authority" in
        localhost|127.0.0.1|'[::1]') ;;
        localhost:*|127.0.0.1:*|'[::1]':*) port="${authority##*:}" ;;
        *) return 1 ;;
      esac
      case "$port" in ''|*[!0-9]*) return 1 ;; esac
      ;;
    *) return 1 ;;
  esac
  return 0
}

endpoint="${HIVE_LF_ENDPOINT:-${BASE_URL%/}$TRACES_PATH}"
validate_endpoint "$endpoint" || fail 'endpoint invalido (https, ou http so em localhost; path exato; sem query/fragmento/userinfo)'

code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 5 \
  "${BASE_URL%/}$HEALTH_PATH" 2>/dev/null) || fail 'health inalcançavel'
[ "$code" = '200' ] || fail "health HTTP $code (esperado 200)"

dir="${ENV_FILE%/*}"
[ "$dir" != "$ENV_FILE" ] || dir='.'
[ -d "$dir" ] || fail 'diretorio de destino ausente'
umask 077
tmp=$(mktemp "$dir/.${ENV_FILE##*/}.XXXXXX") || fail 'falha ao criar arquivo temporario'
trap 'rm -f -- "$tmp"' EXIT

{
  printf '%s\n' '# gerado por scripts/setup-hive-langfuse.sh - nao editar manualmente'
  printf '%s\n' 'HIVE_TELEMETRY_ENABLED=true'
  printf 'LANGFUSE_OTLP_TRACES_ENDPOINT=%s\n' "$endpoint"
  printf 'LANGFUSE_INGESTION_PK=%s\n' "$HIVE_LF_PK"
  printf 'LANGFUSE_INGESTION_SK=%s\n' "$HIVE_LF_SK"
} > "$tmp"

changed=0
if [ -e "$ENV_FILE" ]; then
  if cmp -s "$tmp" "$ENV_FILE"; then
    changed=0
  elif [ "$FORCE" -eq 1 ]; then
    mv -f -- "$tmp" "$ENV_FILE"
    chmod 600 -- "$ENV_FILE"
    changed=1
  else
    fail 'env de destino existe com conteudo diferente; use --force'
  fi
else
  mv -f -- "$tmp" "$ENV_FILE"
  chmod 600 -- "$ENV_FILE"
  changed=1
fi

if [ "$changed" -eq 1 ]; then state='criado/atualizado'; else state='inalterado'; fi
printf 'setup: health 200; env=%s; estado=%s; 4 vars; pk-lf-*** sk-lf-***\n' "$ENV_FILE" "$state"

if [ "$RUN_IT" -eq 1 ]; then
  [ -n "${HIVE_LF_VIEWER_AUTH:-}" ] || fail '--it exige HIVE_LF_VIEWER_AUTH'
  [ -n "${HIVE_LF_FULL_AUTH:-}" ] || fail '--it exige HIVE_LF_FULL_AUTH'
  it="$ROOT/retention-job/src/it/key-scope-it.sh"
  [ -f "$it" ] || fail 'IT de chaves ausente no repo'
  basic=$(printf '%s:%s' "$HIVE_LF_PK" "$HIVE_LF_SK" | base64 | tr -d '\n')
  cutoff=$(date -u -d '30 days ago' '+%Y-%m-%dT%H:%M:%SZ')
  printf 'setup: disparando IT de chaves (cutoff=%s); credenciais redatadas\n' "$cutoff"
  LANGFUSE_REAL_BASE_URL="${BASE_URL%/}" \
  APPLICATION_AUTH="$basic" \
  VIEWER_AUTH="$HIVE_LF_VIEWER_AUTH" \
  FULL_AUTH="$HIVE_LF_FULL_AUTH" \
  CUTOFF="$cutoff" \
    bash "$it"
fi
